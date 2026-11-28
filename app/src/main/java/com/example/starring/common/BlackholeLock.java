package com.example.starring.common;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.starring.court.CourtRepository;

import java.util.function.BooleanSupplier;

/**
 * 블랙홀 잠금 (교수님 피드백: 블랙홀이 있는 동안 나머지 기능 정지).
 *
 * 활성 판단은 {@link CourtRepository#hasOpenBlackhole()} — 우리 은하계의 재판 글 중 화해 전(resolvedAt 없음)이 있으면 잠김.
 * 마감이 지나도 화해 전이면 계속 잠김 (별을 삼키는 StarConsumption과 같은 기준).
 *
 * 막는 것: 행성 생성·사진 추가, 은하계 이름 변경, 행성 둘러보기·하트, 행성 편집·공개 전환·테마.
 * 허용: 블랙홀 투표/화해, 우주 재판장, 로그아웃, 은하계 소멸, 채팅, 행성 보기.
 *
 * 앱 쪽 차단만 한다 (rules/스키마는 그대로). 재판 데이터는 비동기로 도착하므로
 * 화면 진입 시 + 데이터 도착 시({@link #guardScreen}) + 버튼을 누르는 순간({@link #blockIfLocked}) 세 번 확인한다.
 */
public final class BlackholeLock {

    public static final String MESSAGE =
            "블랙홀이 해결될 때까지 사용할 수 없어요. 우주 재판장에서 먼저 화해해 주세요.";

    private BlackholeLock() {
    }

    public static boolean isLocked() {
        return CourtRepository.hasOpenBlackhole();
    }

    /** 잠겨 있으면 안내 문구를 띄우고 true (버튼 클릭 맨 앞에서 사용) */
    public static boolean blockIfLocked(@NonNull Context context) {
        if (!isLocked()) {
            return false;
        }
        Toast.makeText(context, MESSAGE, Toast.LENGTH_SHORT).show();
        return true;
    }

    /** 화면 전체가 잠금 대상일 때: 잠겨 있으면 안내 후 화면을 닫는다. */
    public static void guardScreen(@NonNull Activity activity) {
        guardScreen(activity, () -> true);
    }

    /**
     * 화면이 보이는 동안(onResume~onPause) 재판 데이터를 지켜보다가 잠기면 안내 후 닫는다.
     * 진입 직후에는 데이터가 아직 없을 수 있어서, 데이터가 도착할 때마다 다시 확인한다.
     * onCreate에서 한 번 호출 (팀원 화면이 Activity/AppCompatActivity 섞여 있어 Application 콜백 사용).
     *
     * @param canClose 저장/업로드 중처럼 지금 닫으면 안 될 때 false
     */
    public static void guardScreen(@NonNull Activity activity,
                                   @NonNull BooleanSupplier canClose) {
        Runnable check = () -> {
            if (activity.isFinishing() || activity.isDestroyed()) {
                return;
            }
            if (isLocked() && canClose.getAsBoolean()) {
                Toast.makeText(activity, MESSAGE, Toast.LENGTH_SHORT).show();
                activity.finish();
            }
        };
        Application app = activity.getApplication();
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityResumed(@NonNull Activity a) {
                if (a == activity) {
                    CourtRepository.addObserver(check);
                    check.run();
                }
            }

            @Override
            public void onActivityPaused(@NonNull Activity a) {
                if (a == activity) {
                    CourtRepository.removeObserver(check);
                }
            }

            @Override
            public void onActivityDestroyed(@NonNull Activity a) {
                if (a == activity) {
                    CourtRepository.removeObserver(check);
                    app.unregisterActivityLifecycleCallbacks(this);
                }
            }

            @Override
            public void onActivityCreated(@NonNull Activity a, @Nullable Bundle b) {
            }

            @Override
            public void onActivityStarted(@NonNull Activity a) {
            }

            @Override
            public void onActivityStopped(@NonNull Activity a) {
            }

            @Override
            public void onActivitySaveInstanceState(@NonNull Activity a, @NonNull Bundle b) {
            }
        });
    }
}

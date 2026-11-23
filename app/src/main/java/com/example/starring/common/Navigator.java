package com.example.starring.common;

import android.app.Activity;
import android.content.Intent;


import com.example.starring.MainActivity;
import com.example.starring.auth.LoginActivity;
import com.example.starring.auth.SignUpActivity;
import com.example.starring.couple.CoupleLinkActivity;
import com.example.starring.galaxy.GalaxyPlaceholderActivity;
import com.example.starring.GalaxySetupActivity;
import com.example.starring.GalaxyLoadingActivity;

/**
 * 화면 이동(Intent 생성)을 한 곳에 모은 클래스.
 * 다른 화면에서 Activity를 직접 new Intent(...) 하지 말고 여기를 통해 이동한다.
 * → 메인 화면이 팀원 화면으로 바뀌면 {@link #toMain(Activity)} 한 군데만 수정하면 된다.
 */
public final class Navigator {

    private Navigator() {
    }

    /** 로그인 상태를 다시 확인해서 분기 (MainActivity = Splash). 뒤로가기 스택 초기화. */
    public static void toSplash(Activity from) {
        startClearTask(from, MainActivity.class);
    }

    public static void toLogin(Activity from) {
        startClearTask(from, LoginActivity.class);
    }

    /** 회원가입은 로그인 화면 위에 쌓는다 (뒤로가기로 로그인 화면 복귀). */
    public static void toSignUp(Activity from) {
        from.startActivity(new Intent(from, SignUpActivity.class));
    }

    public static void toCoupleLink(Activity from) {
        startClearTask(from, CoupleLinkActivity.class);
    }

    /**
     * 메인(은하계) 화면.
     * TODO: 팀원의 메인 화면이 완성되면 GalaxyPlaceholderActivity만 교체.
     */

    // 만난 날짜 / 은하계 이름 설정
    public static void toGalaxySetup(Activity from) {
        startClearTask(from, GalaxySetupActivity.class);
    }

    // 은하계 로딩 → 실제 메인
    public static void toMain(Activity from) {
        startClearTask(from, GalaxyLoadingActivity.class);
    }

    private static void startClearTask(Activity from, Class<? extends Activity> target) {
        Intent intent = new Intent(from, target);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        from.startActivity(intent);
        from.finish();
    }
}

package com.example.starring.perf;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Debug;
import android.os.SystemClock;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleCallback;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.starring.GalaxyActivity;
import com.example.starring.GalaxyLoadingActivity;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertTrue;

/**
 * GalaxyActivity 첫 로딩 측정 (화면 조작 없음, 측정 전용).
 *
 * 태양 아이콘(레이아웃)과 태양계 메뉴 8개(createPlanetMenu), GalaxyView 생성이 전부 onCreate 안에서 일어나므로
 * onCreate 시작~끝 시간과 그 직후 메모리를 잰다. 로그인 여부와 무관하게 같은 구간을 잴 수 있다
 * (로그인 안 된 기기에서는 onStart에서 로그인 화면으로 넘어가지만, 그 전에 onCreate는 끝난 상태).
 *
 * 프로세스를 새로 띄운 첫 실행만 의미가 있으므로 한 번 실행에 한 번만 잰다 → am instrument로 여러 번 실행.
 * 결과는 logcat 태그 "LaunchPerf"로 출력.
 *
 * 실행 (앱 데이터가 지워지지 않도록 connectedAndroidTest 대신):
 *   ./gradlew installDebug installDebugAndroidTest
 *   adb shell am instrument -w -e class com.example.starring.perf.GalaxyLaunchPerfTest \
 *       com.example.starring.test/androidx.test.runner.AndroidJUnitRunner
 */
@RunWith(AndroidJUnit4.class)
public class GalaxyLaunchPerfTest {

    private static final String TAG = "LaunchPerf";

    @Test
    public void measureFirstOnCreate() throws Exception {
        final long[] t = new long[2];
        final double[][] mem = new double[2][];
        final CountDownLatch created = new CountDownLatch(1);

        ActivityLifecycleCallback callback = (Activity activity, Stage stage) -> {
            if (!(activity instanceof GalaxyActivity)) {
                return;
            }
            if (stage == Stage.PRE_ON_CREATE) {
                mem[0] = memory();                    // 측정 비용이 구간에 안 들어가도록 먼저
                t[0] = SystemClock.elapsedRealtimeNanos();
            } else if (stage == Stage.CREATED && t[1] == 0) {
                t[1] = SystemClock.elapsedRealtimeNanos();
                mem[1] = memory();
                logImageBitmaps(activity.getWindow().getDecorView());
                created.countDown();
            }
        };
        ActivityLifecycleMonitorRegistry.getInstance().addLifecycleCallback(callback);
        try {
            Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
            Intent intent = new Intent(context, GalaxyActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    // 1년 전 만난 커플 (변형 간 동일 조건)
                    .putExtra(GalaxyLoadingActivity.EXTRA_START_DATE, "2025-11-26");
            context.startActivity(intent);
            assertTrue("GalaxyActivity onCreate timeout", created.await(120, TimeUnit.SECONDS));
        } finally {
            ActivityLifecycleMonitorRegistry.getInstance().removeLifecycleCallback(callback);
        }

        double onCreateMs = (t[1] - t[0]) / 1e6;
        Log.i(TAG, String.format(Locale.US,
                "RESULT onCreateMs=%.0f pssBeforeMB=%.1f pssAfterMB=%.1f nativeBeforeMB=%.1f nativeAfterMB=%.1f",
                onCreateMs, mem[0][0], mem[1][0], mem[0][1], mem[1][1]));
    }

    /** 화면의 ImageView가 들고 있는 비트맵 크기 (읽기만 함) */
    private static void logImageBitmaps(android.view.View root) {
        java.util.ArrayDeque<android.view.View> q = new java.util.ArrayDeque<>();
        q.add(root);
        int count = 0;
        long bytes = 0;
        java.util.Set<android.graphics.Bitmap> seen =
                java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        StringBuilder sizes = new StringBuilder();
        while (!q.isEmpty()) {
            android.view.View v = q.poll();
            if (v instanceof android.view.ViewGroup) {
                android.view.ViewGroup g = (android.view.ViewGroup) v;
                for (int i = 0; i < g.getChildCount(); i++) {
                    q.add(g.getChildAt(i));
                }
            }
            if (v instanceof android.widget.ImageView) {
                android.graphics.drawable.Drawable d = ((android.widget.ImageView) v).getDrawable();
                if (d instanceof android.graphics.drawable.BitmapDrawable) {
                    android.graphics.Bitmap b = ((android.graphics.drawable.BitmapDrawable) d).getBitmap();
                    count++;
                    sizes.append(b.getWidth()).append('x').append(b.getHeight())
                            .append(seen.add(b) ? "" : "(shared)").append(' ');
                }
            }
        }
        for (android.graphics.Bitmap b : seen) {
            bytes += b.getAllocationByteCount();
        }
        Log.i(TAG, String.format(Locale.US, "IMAGES imageViews=%d uniqueBitmaps=%d bytesMB=%.1f sizes=%s",
                count, seen.size(), bytes / 1024.0 / 1024.0, sizes.toString().trim()));
    }

    /** [총 PSS(MB), 네이티브 힙 할당량(MB) — Android 8+ 비트맵 픽셀은 네이티브 힙] */
    private static double[] memory() {
        Debug.MemoryInfo info = new Debug.MemoryInfo();
        Debug.getMemoryInfo(info);
        return new double[]{
                info.getTotalPss() / 1024.0,
                Debug.getNativeHeapAllocatedSize() / 1024.0 / 1024.0};
    }
}

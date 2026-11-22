package com.example.starring.perf;

import android.app.Activity;
import android.os.Debug;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Log;
import android.view.Choreographer;
import android.view.FrameMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.starring.GalaxyActivity;
import com.example.starring.GalaxyView;
import com.example.starring.common.GalaxyDates;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 만난 날이 아주 과거일 때 GalaxyView 성능 측정 (화면 조작 없음, 측정 전용).
 *
 * 실제 GalaxyActivity를 띄우고, 그 안의 GalaxyView에 N년치 값(GalaxyDates로 계산)을 넣은 뒤
 * FrameMetrics로 실제 하드웨어 렌더링 프레임 시간을, Debug.MemoryInfo로 PSS를 잰다.
 * 팀원 코드는 수정하지 않는다 (줌 값만 리플렉션으로 바꿔 "전체 보기" 상황을 재현).
 *
 * 결과는 logcat 태그 "StarPerf"로 출력.
 * 실행: ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.starring.perf.GalaxyStarPerfTest
 */
@RunWith(AndroidJUnit4.class)
public class GalaxyStarPerfTest {

    private static final String TAG = "StarPerf";
    private static final int FRAMES = 60;
    private static final int[] YEARS = {1, 5, 10, 20};

    @Test
    public void measureStarCounts() throws Exception {
        Log.i(TAG, "years|stars|setCountsMs|firstFrameMs|avgFrameMs(zoom1)|p90(zoom1)|avgFrameMs(zoomMin)|p90(zoomMin)|jank%(zoomMin)|pssMB|javaHeapMB|pssBaseMB");
        for (int years : YEARS) {
            measure(years);
        }
    }

    /**
     * GalaxyView 하나만 따로 그린 비용 (화면의 다른 요소·에뮬레이터 합성 비용 제외).
     *  - recordMs : 하드웨어 렌더링에서 UI 스레드가 onDraw를 기록하는 시간 (RenderNode)
     *  - swDrawMs : 소프트웨어 Canvas에 실제로 그리는 시간 (픽셀 작업 포함, 상대 비교용)
     * 0년 = 별 0개 기준선.
     */
    @Test
    public void measureGalaxyViewOnly() throws Exception {
        Log.i(TAG, "ISOLATED years|stars|zoom|recordMs|swDrawMs");
        int[] years = {0, 1, 5, 10, 20};
        try (ActivityScenario<GalaxyActivity> scenario = ActivityScenario.launch(GalaxyActivity.class)) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            SystemClock.sleep(1000);
            AtomicReference<GalaxyView> viewRef = new AtomicReference<>();
            scenario.onActivity(a -> viewRef.set(find(a.getWindow().getDecorView())));
            GalaxyView view = viewRef.get();

            for (int y : years) {
                LocalDate today = GalaxyDates.today();
                LocalDate start = today.minusYears(y);
                int days = GalaxyDates.elapsedDays(start, today);
                int months = GalaxyDates.elapsedMonths(start, today);
                for (float zoom : new float[]{1f, 0.3f}) {
                    double[] r = new double[2];
                    runOnMain(() -> {
                        view.setStarCounts(days, months);
                        setZoom(view, zoom);
                        int w = view.getWidth();
                        int h = view.getHeight();
                        android.graphics.RenderNode node = new android.graphics.RenderNode("perf");
                        node.setPosition(0, 0, w, h);
                        android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(
                                w, h, android.graphics.Bitmap.Config.ARGB_8888);
                        android.graphics.Canvas sw = new android.graphics.Canvas(bmp);
                        for (int i = 0; i < 5; i++) { // 워밍업
                            android.graphics.RecordingCanvas rc = node.beginRecording();
                            view.draw(rc);
                            node.endRecording();
                        }
                        int n = 20;
                        long t0 = System.nanoTime();
                        for (int i = 0; i < n; i++) {
                            android.graphics.RecordingCanvas rc = node.beginRecording();
                            view.draw(rc);
                            node.endRecording();
                        }
                        r[0] = (System.nanoTime() - t0) / 1e6 / n;
                        view.draw(sw); // 워밍업
                        int m = 5;
                        long t1 = System.nanoTime();
                        for (int i = 0; i < m; i++) {
                            view.draw(sw);
                        }
                        r[1] = (System.nanoTime() - t1) / 1e6 / m;
                        node.discardDisplayList();
                        bmp.recycle();
                    });
                    Log.i(TAG, String.format(java.util.Locale.US, "ISOLATED %d|%d|%.1f|%.2f|%.1f",
                            y, days + months, zoom, r[0], r[1]));
                }
            }
        }
    }

    private void measure(int years) throws Exception {
        LocalDate today = GalaxyDates.today();
        LocalDate start = today.minusYears(years);
        int days = GalaxyDates.elapsedDays(start, today);
        int months = GalaxyDates.elapsedMonths(start, today);
        int mask = GalaxyDates.constellationMask(start, today);
        int hundreds = GalaxyDates.hundredDayCount(start, today);
        int yearCount = GalaxyDates.yearCount(start, today);

        try (ActivityScenario<GalaxyActivity> scenario = ActivityScenario.launch(GalaxyActivity.class)) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            SystemClock.sleep(1500);

            AtomicReference<GalaxyView> viewRef = new AtomicReference<>();
            AtomicReference<Activity> actRef = new AtomicReference<>();
            scenario.onActivity(a -> {
                actRef.set(a);
                viewRef.set(find(a.getWindow().getDecorView()));
            });
            GalaxyView view = viewRef.get();
            Window window = actRef.get().getWindow();

            double pssBase = pssMb();

            // 1) 값 반영 (GalaxyActivity.updateGalaxy가 하는 일과 같은 호출)
            long[] setNs = new long[1];
            runOnMain(() -> {
                long t0 = System.nanoTime();
                view.setStarCounts(days, months);
                view.setConstellationMask(mask);
                view.setSpecialBodyCounts(hundreds, yearCount);
                view.setPlanetCount(months);
                setNs[0] = System.nanoTime() - t0;
            });

            // 2) 기본 줌(1.0) 프레임 — 첫 프레임 = 값 반영 직후 렌더링
            List<Long> zoom1 = collectFrames(window, view);

            // 3) 최소 줌(0.3) = 은하계 전체가 화면에 들어오는 상황 (별을 거의 다 그림)
            runOnMain(() -> setZoom(view, 0.3f));
            List<Long> zoomMin = collectFrames(window, view);

            Runtime rt = Runtime.getRuntime();
            rt.gc();
            SystemClock.sleep(300);
            double pss = pssMb();
            double heap = (rt.totalMemory() - rt.freeMemory()) / 1048576.0;

            int stars = days + months;
            Log.i(TAG, String.format(java.util.Locale.US,
                    "%d|%d|%.1f|%.1f|%.1f|%.1f|%.1f|%.1f|%.0f|%.1f|%.1f|%.1f",
                    years, stars, setNs[0] / 1e6,
                    zoom1.get(0) / 1e6,
                    avg(zoom1) / 1e6, pct(zoom1, 0.9) / 1e6,
                    avg(zoomMin) / 1e6, pct(zoomMin, 0.9) / 1e6,
                    jankPct(zoomMin),
                    pss, heap, pssBase));
        }
    }

    /** invalidate를 매 프레임 걸어 FRAMES개 프레임의 TOTAL_DURATION(ns)을 모은다. */
    private List<Long> collectFrames(Window window, View view) throws Exception {
        List<Long> totals = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch done = new CountDownLatch(FRAMES);
        HandlerThread ht = new HandlerThread("frameMetrics");
        ht.start();
        Window.OnFrameMetricsAvailableListener listener = (w, metrics, dropped) -> {
            if (done.getCount() > 0) {
                totals.add(metrics.getMetric(FrameMetrics.TOTAL_DURATION));
                done.countDown();
            }
        };
        runOnMain(() -> {
            window.addOnFrameMetricsAvailableListener(listener, new Handler(ht.getLooper()));
            Choreographer.getInstance().postFrameCallback(new Choreographer.FrameCallback() {
                @Override
                public void doFrame(long frameTimeNanos) {
                    if (done.getCount() > 0) {
                        view.invalidate();
                        Choreographer.getInstance().postFrameCallback(this);
                    }
                }
            });
            view.invalidate();
        });
        done.await(30, TimeUnit.SECONDS);
        runOnMain(() -> window.removeOnFrameMetricsAvailableListener(listener));
        ht.quitSafely();
        return new ArrayList<>(totals);
    }

    private static void setZoom(GalaxyView view, float zoom) {
        try {
            Field f = GalaxyView.class.getDeclaredField("zoom");
            f.setAccessible(true);
            f.setFloat(view, zoom);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        view.invalidate();
    }

    private static GalaxyView find(View v) {
        if (v instanceof GalaxyView) {
            return (GalaxyView) v;
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                GalaxyView found = find(g.getChildAt(i));
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static void runOnMain(Runnable r) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(r);
    }

    private static double pssMb() {
        Debug.MemoryInfo info = new Debug.MemoryInfo();
        Debug.getMemoryInfo(info);
        return info.getTotalPss() / 1024.0;
    }

    private static double avg(List<Long> v) {
        long sum = 0;
        for (long x : v) {
            sum += x;
        }
        return v.isEmpty() ? 0 : (double) sum / v.size();
    }

    private static long pct(List<Long> v, double p) {
        if (v.isEmpty()) {
            return 0;
        }
        List<Long> s = new ArrayList<>(v);
        Collections.sort(s);
        return s.get(Math.min(s.size() - 1, (int) Math.floor(p * s.size())));
    }

    /** 16.7ms(60fps 한 프레임)를 넘긴 프레임 비율 */
    private static double jankPct(List<Long> v) {
        if (v.isEmpty()) {
            return 0;
        }
        int n = 0;
        for (long x : v) {
            if (x > 16_700_000L) {
                n++;
            }
        }
        return 100.0 * n / v.size();
    }
}

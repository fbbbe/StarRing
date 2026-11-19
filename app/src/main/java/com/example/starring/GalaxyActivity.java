package com.example.starring;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.FrameLayout;

import java.util.Calendar;
import java.util.TimeZone;

public class GalaxyActivity extends Activity {

    private GalaxyView galaxyView;

    private final Handler handler =
            new Handler(Looper.getMainLooper());

    // 실제 연애 시작일 (현재 테스트용)
    private static final int START_YEAR = 2026;
    private static final int START_MONTH = 11;
    private static final int START_DAY = 19;

    // 테스트용: 실제 날짜에 며칠을 더할지
    private static final int TEST_ADD_DAYS = 100;

    private final Runnable dateChecker = new Runnable() {
        @Override
        public void run() {
            updateGalaxy();
            handler.postDelayed(this, 60000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_galaxy);

        FrameLayout container =
                findViewById(R.id.galaxyContainer);

        galaxyView = new GalaxyView(this);

        container.addView(
                galaxyView,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                )
        );
    }

    @Override
    protected void onResume() {
        super.onResume();

        handler.removeCallbacks(dateChecker);
        handler.post(dateChecker);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(dateChecker);
        super.onPause();
    }

    // 날짜만 비교하기 위한 UTC 달력
    private Calendar makeDate(
            int year, int month, int day) {

        Calendar date = Calendar.getInstance(
                TimeZone.getTimeZone("UTC")
        );

        date.clear();
        date.set(year, month - 1, day);

        return date;
    }

    // 별 개수 갱신
    private void updateGalaxy() {

        Calendar localNow = Calendar.getInstance();

        Calendar today = makeDate(
                localNow.get(Calendar.YEAR),
                localNow.get(Calendar.MONTH) + 1,
                localNow.get(Calendar.DAY_OF_MONTH)
        );

        // 개발 중 테스트 날짜 이동
        today.add(
                Calendar.DAY_OF_MONTH,
                TEST_ADD_DAYS
        );

        Calendar start = makeDate(
                START_YEAR,
                START_MONTH,
                START_DAY
        );

        // 경과 일수
        long diff = today.getTimeInMillis()
                - start.getTimeInMillis();

        int days = (int) Math.max(
                0,
                diff / 86400000L
        );

        // 경과 개월 수
        int months = 0;

        if (!today.before(start)) {

            while (true) {

                Calendar next = (Calendar) start.clone();

                next.add(
                        Calendar.MONTH,
                        months + 1
                );

                if (next.after(today)) {
                    break;
                }

                months++;
            }
        }

        // 은하계 화면 갱신
        galaxyView.setStarCounts(days, months);
    }
}

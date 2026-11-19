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

    // 연애 시작일 (테스트용)
    private static final int START_YEAR = 2026;
    private static final int START_MONTH = 11;
    private static final int START_DAY = 19;

    // 날짜 테스트용
    private static final int TEST_ADD_DAYS =360;

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

    // 날짜 계산용 Calendar 생성
    private Calendar makeDate(
            int year, int month, int day) {

        Calendar date = Calendar.getInstance(
                TimeZone.getTimeZone("UTC")
        );

        date.clear();
        date.set(year, month - 1, day);

        return date;
    }

    // 매월 1일에 생성할 별자리 계산
    private int calculateConstellationMask(
            Calendar start, Calendar today) {

        if (today.before(start)) {
            return 0;
        }

        // 연애 시작일이 속한 달의 1일
        Calendar first = (Calendar) start.clone();
        first.set(Calendar.DAY_OF_MONTH, 1);

        // 연애 시작 전의 별자리는 생성하지 않음
        if (first.before(start)) {
            first.add(Calendar.MONTH, 1);
        }

        int mask = 0;

        while (!first.after(today)) {

            // Calendar.MONTH는 0~11
            int monthIndex = first.get(Calendar.MONTH);

            // 해당 월의 비트가 이미 1이면 중복 생성 안 함
            mask |= (1 << monthIndex);

            // 12종 모두 생성되면 종료
            if (mask == 0xFFF) {
                break;
            }

            first.add(Calendar.MONTH, 1);
        }

        return mask;
    }

    // 은하계 갱신
    private void updateGalaxy() {

        Calendar localNow = Calendar.getInstance();

        Calendar today = makeDate(
                localNow.get(Calendar.YEAR),
                localNow.get(Calendar.MONTH) + 1,
                localNow.get(Calendar.DAY_OF_MONTH)
        );

        // 테스트용 날짜 이동
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

        // 애기별 및 큰 별 갱신
        galaxyView.setStarCounts(days, months);

        // 별자리 생성 여부 갱신
        int constellationMask =
                calculateConstellationMask(start, today);

        galaxyView.setConstellationMask(constellationMask);
        // 행성 6개 테스트 생성
        galaxyView.setPlanetCount(6);
    }
}

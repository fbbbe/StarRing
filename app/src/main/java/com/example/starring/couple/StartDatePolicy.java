package com.example.starring.couple;

import com.example.starring.common.GalaxyDates;

import java.time.LocalDate;

/**
 * 만난 날(startDate) 입력 규칙. firestore.rules의 validStartDate()와 같은 기준이어야 한다.
 *
 * - 저장 형식: 고른 날짜의 UTC 00:00 (Timestamp)
 * - 하한: 1950-01-01 (오타 방지용. 오래 만난 커플·부부를 막지 않도록 20년 같은 제한은 두지 않음)
 * - 상한: 오늘. rules는 시간대 차이를 고려해 request.time + 1일까지 허용.
 */
public final class StartDatePolicy {

    private StartDatePolicy() {
    }

    public static final LocalDate MIN_DATE = LocalDate.of(1950, 1, 1);

    /** 1950-01-01T00:00:00Z */
    public static final long MIN_UTC_MILLIS = GalaxyDates.toUtcMidnightMillis(MIN_DATE);

    private static final long DAY_MILLIS = 24L * 60 * 60 * 1000;

    /**
     * 저장해도 되는 값인지. (DatePicker가 이미 막지만 저장 직전에 한 번 더 확인)
     * @param utcMidnightMillis DatePicker 선택값 (UTC 00:00 millis)
     * @param nowMillis 현재 시각. 상한 판단 편의용으로만 쓰고 저장하지 않음 (최종 판단은 rules의 request.time)
     */
    public static boolean isValid(long utcMidnightMillis, long nowMillis) {
        return Math.floorMod(utcMidnightMillis, DAY_MILLIS) == 0
                && utcMidnightMillis >= MIN_UTC_MILLIS
                && utcMidnightMillis <= nowMillis + DAY_MILLIS;
    }
}

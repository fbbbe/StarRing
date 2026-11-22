package com.example.starring.couple;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.starring.common.GalaxyDates;

import org.junit.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** firestore.rules의 validStartDate()와 같은 기준인지 확인 */
public class StartDatePolicyTest {

    private static long utc(int y, int m, int d) {
        return GalaxyDates.toUtcMidnightMillis(LocalDate.of(y, m, d));
    }

    private static long kst(int y, int m, int d, int h) {
        return ZonedDateTime.of(y, m, d, h, 0, 0, 0, ZoneId.of("Asia/Seoul"))
                .toInstant().toEpochMilli();
    }

    @Test
    public void lowerBound() {
        long now = kst(2026, 11, 20, 12);
        assertTrue(StartDatePolicy.isValid(utc(1950, 1, 1), now));
        assertFalse(StartDatePolicy.isValid(utc(1949, 12, 31), now));
    }

    @Test
    public void todayAllowed_evenBeforeKst9am() {
        // KST 11-20 08:00 = UTC 11-19 23:00. 오늘(11-20) UTC 00:00은 서버 시각보다 1시간 미래지만 허용
        long now = kst(2026, 11, 20, 8);
        assertTrue(StartDatePolicy.isValid(utc(2026, 11, 20), now));
    }

    @Test
    public void futureRejected() {
        long now = kst(2026, 11, 20, 12);
        assertFalse(StartDatePolicy.isValid(utc(2026, 11, 22), now));
    }

    @Test
    public void nonMidnightRejected() {
        long now = kst(2026, 11, 20, 12);
        assertFalse(StartDatePolicy.isValid(utc(2024, 5, 12) + 1000, now));
        assertFalse(StartDatePolicy.isValid(kst(2024, 5, 12, 0), now)); // KST 자정 ≠ UTC 자정
    }
}

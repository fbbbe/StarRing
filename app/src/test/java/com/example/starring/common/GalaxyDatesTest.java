package com.example.starring.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

public class GalaxyDatesTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private static LocalDate d(int y, int m, int day) {
        return LocalDate.of(y, m, day);
    }

    // ───────── 기준일 ─────────

    @Test
    public void effectiveStart_prefersDatingStartDate() {
        Date bornAt = Date.from(ZonedDateTime.of(2026, 11, 19, 10, 0, 0, 0, KST).toInstant());
        assertEquals(d(2024, 5, 12), GalaxyDates.effectiveStart("2024-05-12", bornAt, KST));
    }

    @Test
    public void effectiveStart_datingStartDateIgnoresDeviceZone() {
        // 문자열 날짜는 어느 시간대 기기에서 읽어도 같은 날짜
        assertEquals(d(2024, 5, 12),
                GalaxyDates.effectiveStart("2024-05-12", null, ZoneId.of("America/Los_Angeles")));
        assertEquals(d(2024, 5, 12),
                GalaxyDates.effectiveStart("2024-05-12", null, ZoneId.of("Pacific/Kiritimati")));
    }

    @Test
    public void effectiveStart_brokenDatingStartDateFallsBackToBornAt() {
        Date bornAt = Date.from(ZonedDateTime.of(2026, 11, 20, 12, 0, 0, 0, KST).toInstant());
        assertEquals(d(2026, 11, 20), GalaxyDates.effectiveStart("2024-02-31", bornAt, KST));
        assertEquals(d(2026, 11, 20), GalaxyDates.effectiveStart("", bornAt, KST));
    }

    @Test
    public void effectiveStart_fallsBackToBornAtLocalDate() {
        // KST 2026-11-20 08:00 = UTC 11-19 23:00 → KST 기기에서는 11-20
        Date bornAt = Date.from(ZonedDateTime.of(2026, 11, 20, 8, 0, 0, 0, KST).toInstant());
        assertEquals(d(2026, 11, 20), GalaxyDates.effectiveStart(null, bornAt, KST));
    }

    @Test
    public void effectiveStart_nullWhenNothing() {
        assertNull(GalaxyDates.effectiveStart((String) null, null, KST));
    }

    // ───────── 문자열 변환 ─────────

    @Test
    public void parseDate_valid() {
        assertEquals(d(2024, 2, 29), GalaxyDates.parseDate("2024-02-29"));
        assertEquals(d(1950, 1, 1), GalaxyDates.parseDate("1950-01-01"));
    }

    @Test
    public void parseDate_rejectsNonexistentDates() {
        assertNull(GalaxyDates.parseDate("2024-02-31"));
        assertNull(GalaxyDates.parseDate("2023-02-29"));  // 평년
        assertNull(GalaxyDates.parseDate("2024-13-01"));
        assertNull(GalaxyDates.parseDate("2024-00-10"));
    }

    @Test
    public void parseDate_rejectsBadFormat() {
        assertNull(GalaxyDates.parseDate(null));
        assertNull(GalaxyDates.parseDate(""));
        assertNull(GalaxyDates.parseDate("2024-5-12"));
        assertNull(GalaxyDates.parseDate("2024/05/12"));
        assertNull(GalaxyDates.parseDate(" 2024-05-12"));
        assertNull(GalaxyDates.parseDate("+2024-05-12"));
    }

    @Test
    public void formatDate_roundTrip() {
        assertEquals("2024-05-12", GalaxyDates.formatDate(d(2024, 5, 12)));
        assertEquals("1950-01-01", GalaxyDates.formatDate(d(1950, 1, 1)));
        assertEquals(d(2024, 5, 12), GalaxyDates.parseDate(GalaxyDates.formatDate(d(2024, 5, 12))));
    }

    // ───────── 일수 ─────────

    @Test
    public void elapsedDays_andDayCount() {
        LocalDate s = d(2024, 5, 12);
        assertEquals(0, GalaxyDates.elapsedDays(s, s));
        assertEquals(1, GalaxyDates.dayCount(s, s));          // 만난 날 = 1일
        assertEquals(99, GalaxyDates.elapsedDays(s, d(2024, 8, 19)));
        assertEquals(100, GalaxyDates.dayCount(s, d(2024, 8, 19)));
        assertEquals(0, GalaxyDates.elapsedDays(s, d(2024, 5, 1)));  // 시작 전
        assertEquals(0, GalaxyDates.dayCount(s, d(2024, 5, 1)));
    }

    @Test
    public void hundredDayCount() {
        LocalDate s = d(2024, 5, 12);
        assertEquals(0, GalaxyDates.hundredDayCount(s, d(2024, 8, 18)));  // 99일
        assertEquals(1, GalaxyDates.hundredDayCount(s, d(2024, 8, 19)));  // 100일
        assertEquals(2, GalaxyDates.hundredDayCount(s, d(2024, 11, 27))); // 200일
    }

    // ───────── 개월 / 년 ─────────

    @Test
    public void elapsedMonths_basic() {
        LocalDate s = d(2024, 5, 12);
        assertEquals(0, GalaxyDates.elapsedMonths(s, d(2024, 6, 11)));
        assertEquals(1, GalaxyDates.elapsedMonths(s, d(2024, 6, 12)));
        assertEquals(12, GalaxyDates.elapsedMonths(s, d(2025, 5, 12)));
        assertEquals(0, GalaxyDates.elapsedMonths(s, d(2024, 1, 1)));
    }

    @Test
    public void elapsedMonths_endOfMonth() {
        // 1/31 + 1개월 = 2/29(윤년) → 2/29에 1개월
        assertEquals(1, GalaxyDates.elapsedMonths(d(2024, 1, 31), d(2024, 2, 29)));
        assertEquals(0, GalaxyDates.elapsedMonths(d(2024, 1, 31), d(2024, 2, 28)));
    }

    @Test
    public void yearCount() {
        LocalDate s = d(2024, 5, 12);
        assertEquals(0, GalaxyDates.yearCount(s, d(2025, 5, 11)));
        assertEquals(1, GalaxyDates.yearCount(s, d(2025, 5, 12)));
        assertEquals(20, GalaxyDates.yearCount(d(2006, 1, 1), d(2026, 1, 1)));
        // 2/29 시작 → 평년 2/28이 1주년
        assertEquals(1, GalaxyDates.yearCount(d(2024, 2, 29), d(2025, 2, 28)));
    }

    // ───────── 별자리 ─────────

    @Test
    public void constellationMask_startOnFirstIncludesThatMonth() {
        assertEquals(1 << 4, GalaxyDates.constellationMask(d(2024, 5, 1), d(2024, 5, 20)));
    }

    @Test
    public void constellationMask_midMonthStartsNextMonth() {
        assertEquals(0, GalaxyDates.constellationMask(d(2024, 5, 12), d(2024, 5, 31)));
        assertEquals((1 << 5) | (1 << 6),
                GalaxyDates.constellationMask(d(2024, 5, 12), d(2024, 7, 1)));
    }

    @Test
    public void constellationMask_allAfterAYear() {
        assertEquals(0xFFF, GalaxyDates.constellationMask(d(2024, 5, 12), d(2025, 5, 1)));
        assertEquals(0xFFF, GalaxyDates.constellationMask(d(2006, 1, 1), d(2026, 11, 20)));
    }

    // ───────── GalaxyActivity 기존 계산과 같은 결과인지 ─────────

    @Test
    public void matchesGalaxyActivityCalendarLogic() {
        LocalDate[] starts = {d(2024, 1, 31), d(2024, 2, 29), d(2023, 5, 12),
                d(2025, 12, 1), d(2016, 8, 31)};
        for (LocalDate start : starts) {
            for (int add = 0; add < 800; add += 7) {
                LocalDate today = start.plusDays(add);
                Calendar s = utc(start);
                Calendar t = utc(today);
                assertEquals("days " + start + "+" + add,
                        legacyDays(s, t), GalaxyDates.elapsedDays(start, today));
                assertEquals("months " + start + "+" + add,
                        legacyMonths(s, t), GalaxyDates.elapsedMonths(start, today));
                assertEquals("mask " + start + "+" + add,
                        legacyMask(s, t), GalaxyDates.constellationMask(start, today));
            }
        }
    }

    // 아래는 GalaxyActivity.makeDate / updateGalaxy / calculateConstellationMask 복사본

    private static Calendar utc(LocalDate date) {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        c.clear();
        c.set(date.getYear(), date.getMonthValue() - 1, date.getDayOfMonth());
        return c;
    }

    private static int legacyDays(Calendar start, Calendar today) {
        long diff = today.getTimeInMillis() - start.getTimeInMillis();
        return (int) Math.max(0, diff / 86400000L);
    }

    private static int legacyMonths(Calendar start, Calendar today) {
        int months = 0;
        if (!today.before(start)) {
            while (true) {
                Calendar next = (Calendar) start.clone();
                next.add(Calendar.MONTH, months + 1);
                if (next.after(today)) {
                    break;
                }
                months++;
            }
        }
        return months;
    }

    private static int legacyMask(Calendar start, Calendar today) {
        if (today.before(start)) {
            return 0;
        }
        Calendar first = (Calendar) start.clone();
        first.set(Calendar.DAY_OF_MONTH, 1);
        if (first.before(start)) {
            first.add(Calendar.MONTH, 1);
        }
        int mask = 0;
        while (!first.after(today)) {
            mask |= (1 << first.get(Calendar.MONTH));
            if (mask == 0xFFF) {
                break;
            }
            first.add(Calendar.MONTH, 1);
        }
        return mask;
    }
}

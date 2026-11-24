package com.example.starring.common;

import androidx.annotation.Nullable;

import com.example.starring.data.FirestoreSchema;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * 만난 날(startDate) 기준 날짜 계산. 별 개수 / 별자리 / D-day는 전부 여기서 계산한다.
 * (DB에는 startDate만 저장하고 나머지는 저장하지 않는다 — CLAUDE.md 설계 규칙)
 *
 * GalaxyActivity.updateGalaxy()의 계산을 그대로 옮긴 것. 연결할 때는
 *   LocalDate start = GalaxyDates.effectiveStart(galaxySnapshot);
 *   if (start == null) return;            // 아직 기준일 없음 → 그리지 않고 대기
 *   LocalDate today = GalaxyDates.today();
 *   galaxyView.setStarCounts(GalaxyDates.elapsedDays(start, today),
 *                            GalaxyDates.elapsedMonths(start, today));
 *   galaxyView.setConstellationMask(GalaxyDates.constellationMask(start, today));
 * 처럼 START_YEAR/MONTH/DAY 하드코딩 자리만 바꾸면 된다.
 *
 * Android/Firebase에 의존하지 않는 메서드(LocalDate 인자)는 로컬 유닛 테스트 대상.
 */
public final class GalaxyDates {

    private GalaxyDates() {
    }

    // ───────────────────────── 기준일 ─────────────────────────

    /** 은하계 문서에서 기준일을 꺼낸다. 기기 시간대 기준. */
    @Nullable
    public static LocalDate effectiveStart(@Nullable DocumentSnapshot galaxy) {
        if (galaxy == null || !galaxy.exists()) {
            return null;
        }
        LocalDate dating = parseDate(galaxy.getString(FirestoreSchema.GALAXY_DATING_START_DATE));
        if (dating != null) {
            return dating;
        }
        Timestamp bornAt = galaxy.getTimestamp(FirestoreSchema.GALAXY_BORN_AT);
        return effectiveStart(null, bornAt == null ? null : bornAt.toDate(), ZoneId.systemDefault());
    }

    /** "yyyy-MM-dd" → 날짜. 형식이 틀리거나 없는 날짜(2024-02-31)면 null. */
    @Nullable
    public static LocalDate parseDate(@Nullable String yyyyMMdd) {
        if (yyyyMMdd == null || !yyyyMMdd.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return null;
        }
        try {
            return LocalDate.parse(yyyyMMdd);
        } catch (java.time.format.DateTimeParseException e) {
            return null;
        }
    }

    /**
     * 기준일 결정.
     * 1) startDate(UTC 00:00으로 저장된 만난 날) → 그 UTC 날짜
     * 2) 없으면(startDate 도입 전 은하계) bornAt을 zone 기준 날짜로
     * 3) 둘 다 없으면 null (이름 설정 전이거나 serverTimestamp 확정 전) → 호출부는 그리지 않고 대기
     */
    @Nullable
    public static LocalDate effectiveStart(@Nullable Date startDateUtcMidnight,
                                           @Nullable Date bornAt,
                                           ZoneId zone) {
        if (startDateUtcMidnight != null) {
            return utcDateOf(startDateUtcMidnight.getTime());
        }
        if (bornAt != null) {
            return Instant.ofEpochMilli(bornAt.getTime()).atZone(zone).toLocalDate();
        }
        return null;
    }

    /** UTC 00:00 millis → 날짜 (startDate 저장 형식) */
    public static LocalDate utcDateOf(long utcMillis) {
        return Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate();
    }

    /** 날짜 → UTC 00:00 millis (startDate 저장 형식) */
    public static long toUtcMidnightMillis(LocalDate date) {
        return date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
    }

    /** 오늘 날짜 (기기 시간대). GalaxyActivity와 같은 기준. */
    public static LocalDate today() {
        return LocalDate.now(ZoneId.systemDefault());
    }

    // ───────────────────────── 계산 ─────────────────────────

    /** 경과 일수 = 애기별 개수. 만난 날 당일은 0. 미래면 0. */
    public static int elapsedDays(LocalDate start, LocalDate today) {
        return (int) Math.max(0, ChronoUnit.DAYS.between(start, today));
    }

    /**
     * 경과 개월 수 = 큰 별 개수.
     * start + n개월이 오늘 이하인 최대 n. 월말은 Calendar.add와 같게 말일로 맞춘다
     * (1/31 + 1개월 = 2/28 또는 2/29). ChronoUnit.MONTHS.between과 월말 처리가 달라서 직접 센다.
     */
    public static int elapsedMonths(LocalDate start, LocalDate today) {
        if (today.isBefore(start)) {
            return 0;
        }
        int months = 0;
        while (!start.plusMonths(months + 1).isAfter(today)) {
            months++;
        }
        return months;
    }

    /** "탄생 N일" 표시값. 만난 날 당일이 1일 (한국식 기념일 계산). 미래면 0. */
    public static int dayCount(LocalDate start, LocalDate today) {
        if (today.isBefore(start)) {
            return 0;
        }
        return elapsedDays(start, today) + 1;
    }

    /** 100일 특수천체(덜 화려) 개수. 100일, 200일, ... (dayCount 기준) */
    public static int hundredDayCount(LocalDate start, LocalDate today) {
        return dayCount(start, today) / 100;
    }

    /** 1년 특수천체(많이 화려) 개수 = 지난 주년 수. 2/29 시작이면 평년엔 2/28을 기념일로. */
    public static int yearCount(LocalDate start, LocalDate today) {
        if (today.isBefore(start)) {
            return 0;
        }
        int years = 0;
        while (!start.plusYears(years + 1).isAfter(today)) {
            years++;
        }
        return years;
    }

    /**
     * 별자리 생성 비트마스크 (bit 0 = 1월 ... bit 11 = 12월).
     * 만난 날 이후 맞은 매월 1일마다 그 달의 별자리. 만난 날이 1일이면 그 달 포함.
     */
    public static int constellationMask(LocalDate start, LocalDate today) {
        if (today.isBefore(start)) {
            return 0;
        }
        LocalDate first = start.withDayOfMonth(1);
        if (first.isBefore(start)) {
            first = first.plusMonths(1);
        }
        int mask = 0;
        while (!first.isAfter(today)) {
            mask |= 1 << (first.getMonthValue() - 1);
            if (mask == 0xFFF) {
                break;
            }
            first = first.plusMonths(1);
        }
        return mask;
    }
}

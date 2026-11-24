package com.example.starring.common;

import androidx.annotation.Nullable;

import com.example.starring.data.FirestoreSchema;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * 만난 날(datingStartDate) 기준 날짜 계산. 별 개수 / 별자리 / D-day는 전부 여기서 계산한다.
 * (DB에는 datingStartDate만 저장하고 나머지는 저장하지 않는다 — CLAUDE.md 설계 규칙)
 *
 * datingStartDate는 "yyyy-MM-dd" 문자열 (시간대 없는 날짜). 사용 예:
 *   LocalDate start = GalaxyDates.effectiveStart(galaxySnapshot);   // 또는 parseDate(문자열)
 *   if (start == null) return;            // 아직 기준일 없음 → 그리지 않고 대기
 *   LocalDate today = GalaxyDates.today();
 *   galaxyView.setStarCounts(GalaxyDates.elapsedDays(start, today),
 *                            GalaxyDates.elapsedMonths(start, today));
 *   galaxyView.setConstellationMask(GalaxyDates.constellationMask(start, today));
 *
 * Android/Firebase에 의존하지 않는 메서드(LocalDate·String 인자)는 로컬 유닛 테스트 대상.
 */
public final class GalaxyDates {

    private GalaxyDates() {
    }

    // ───────────────────────── 기준일 ─────────────────────────

    /** 은하계 문서에서 기준일을 꺼낸다. bornAt 대체 시에는 기기 시간대 기준. */
    @Nullable
    public static LocalDate effectiveStart(@Nullable DocumentSnapshot galaxy) {
        if (galaxy == null || !galaxy.exists()) {
            return null;
        }
        Timestamp bornAt = galaxy.getTimestamp(FirestoreSchema.GALAXY_BORN_AT);
        return effectiveStart(
                galaxy.getString(FirestoreSchema.GALAXY_DATING_START_DATE),
                bornAt == null ? null : bornAt.toDate(),
                ZoneId.systemDefault());
    }

    /**
     * 기준일 결정.
     * 1) datingStartDate("yyyy-MM-dd")가 올바르면 그 날짜 (시간대 무관)
     * 2) 없거나 깨진 값이면(datingStartDate 도입 전 은하계) bornAt을 zone 기준 날짜로
     * 3) 둘 다 없으면 null (serverTimestamp 확정 전 등) → 호출부는 그리지 않고 대기
     */
    @Nullable
    public static LocalDate effectiveStart(@Nullable String datingStartDate,
                                           @Nullable Date bornAt,
                                           ZoneId zone) {
        LocalDate dating = parseDate(datingStartDate);
        if (dating != null) {
            return dating;
        }
        if (bornAt != null) {
            return Instant.ofEpochMilli(bornAt.getTime()).atZone(zone).toLocalDate();
        }
        return null;
    }

    /** "yyyy-MM-dd" → 날짜. 형식이 틀리거나 없는 날짜(2024-02-31)면 null. */
    @Nullable
    public static LocalDate parseDate(@Nullable String yyyyMMdd) {
        if (yyyyMMdd == null || !yyyyMMdd.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return null;
        }
        try {
            return LocalDate.parse(yyyyMMdd);   // ISO_LOCAL_DATE: 2/31 같은 날짜는 예외
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** 날짜 → "yyyy-MM-dd" (datingStartDate 저장 형식) */
    public static String formatDate(LocalDate date) {
        return date.toString();
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

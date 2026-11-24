package com.example.starring.couple;

import androidx.annotation.Nullable;

import com.example.starring.common.GalaxyDates;

import java.time.LocalDate;

/**
 * 만난 날(datingStartDate, "yyyy-MM-dd") 입력 규칙.
 *
 * firestore.rules의 validDatingStartDate()는 형식(정규식)만 검사한다.
 * 2024-02-31 같은 없는 날짜, 1950년 이전, 미래 날짜는 rules를 통과하므로 앱에서 여기로 막는다.
 *
 * - 하한: 1950-01-01 (오타 방지용. 오래 만난 커플·부부를 막지 않도록 20년 같은 제한은 두지 않음)
 * - 상한: 오늘 (기기 시간대). 날짜 선택기가 이미 막지만 저장 직전에 한 번 더 확인.
 */
public final class StartDatePolicy {

    private StartDatePolicy() {
    }

    public static final LocalDate MIN_DATE = LocalDate.of(1950, 1, 1);

    /**
     * 저장해도 되는 값인지.
     * @param yyyyMMdd 저장하려는 값
     * @param today 오늘 날짜 (보통 {@link GalaxyDates#today()}). 판단에만 쓰고 저장하지 않음
     */
    public static boolean isValid(@Nullable String yyyyMMdd, LocalDate today) {
        LocalDate date = GalaxyDates.parseDate(yyyyMMdd);
        return date != null
                && !date.isBefore(MIN_DATE)
                && !date.isAfter(today);
    }
}

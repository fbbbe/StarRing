package com.example.starring.couple;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDate;

/** rules(validDatingStartDate)는 형식만 보므로, 그 외 조건을 앱에서 막는지 확인 */
public class StartDatePolicyTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 20);

    @Test
    public void lowerBound() {
        assertTrue(StartDatePolicy.isValid("1950-01-01", TODAY));
        assertFalse(StartDatePolicy.isValid("1949-12-31", TODAY));
    }

    @Test
    public void upperBound_todayAllowed_futureRejected() {
        assertTrue(StartDatePolicy.isValid("2026-11-20", TODAY));
        assertFalse(StartDatePolicy.isValid("2026-11-21", TODAY));
    }

    @Test
    public void nonexistentDateRejected() {
        // rules 정규식은 통과하는 값들
        assertFalse(StartDatePolicy.isValid("2024-02-31", TODAY));
        assertFalse(StartDatePolicy.isValid("2023-02-29", TODAY));
        assertFalse(StartDatePolicy.isValid("2024-04-31", TODAY));
        assertTrue(StartDatePolicy.isValid("2024-02-29", TODAY));
    }

    @Test
    public void badFormatRejected() {
        assertFalse(StartDatePolicy.isValid(null, TODAY));
        assertFalse(StartDatePolicy.isValid("", TODAY));
        assertFalse(StartDatePolicy.isValid("2024-5-12", TODAY));
        assertFalse(StartDatePolicy.isValid("20240512", TODAY));
    }
}

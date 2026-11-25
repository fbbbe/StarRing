package com.example.starring;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * "달마다 행성 1개" 규칙 도우미.
 *
 * 새 행성의 문서 ID 를 달 이름("2026-11")으로 만든다.
 *  - 같은 달 행성이 두 개 생길 수 없음 (같은 ID 로는 새로 만들 수 없음)
 *  - firestore.rules 를 바꿀 필요 없음 (필드는 그대로)
 *
 * 예전에 만든 행성(ID 가 랜덤)은 만든 시각(createdAt)의 달로 계산한다.
 */
final class PlanetMonth {

    private PlanetMonth() {
    }

    /** "2026-11" 형태인지 */
    static boolean isMonthKey(String value) {
        return value != null && value.matches("\\d{4}-(0[1-9]|1[0-2])");
    }

    static String key(int year, int month) {
        return String.format(Locale.US, "%04d-%02d", year, month);
    }

    /** 행성 문서가 속한 달 ("2026-11"). 알 수 없으면 null */
    static String of(DocumentSnapshot doc) {

        if (doc == null) return null;

        if (isMonthKey(doc.getId())) {
            return doc.getId();
        }

        Timestamp created = doc.getTimestamp("createdAt");

        if (created == null) return null;

        return new SimpleDateFormat("yyyy-MM", Locale.US)
                .format(new Date(created.toDate().getTime()));
    }

    /** "2026-11" -> "2026년 11월" */
    static String label(String key) {

        if (!isMonthKey(key)) return "";

        return Integer.parseInt(key.substring(0, 4)) + "년 "
                + Integer.parseInt(key.substring(5, 7)) + "월";
    }
}

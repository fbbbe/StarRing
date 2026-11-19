package com.example.starring.couple;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.Random;

/**
 * 6자리 초대코드 생성/정규화/검증.
 * 헷갈리는 문자(0, O, 1, I)는 쓰지 않는다. → firestore.rules 정규식 ^[A-HJ-NP-Z2-9]{6}$ 와 일치해야 함.
 */
public final class InviteCode {

    private InviteCode() {
    }

    public static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    public static final int LENGTH = 6;

    /** 유효기간 24시간. 판단 기준은 firestore.rules의 request.time (서버 시각). */
    public static final long VALID_MILLIS = 24L * 60 * 60 * 1000;

    private static final SecureRandom RANDOM = new SecureRandom();

    public static String generate() {
        return generate(RANDOM);
    }

    static String generate(Random random) {
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    /** 사용자가 입력한 코드 정리: 공백/하이픈 제거, 대문자 변환. */
    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
    }

    public static boolean isValid(String code) {
        if (code == null || code.length() != LENGTH) {
            return false;
        }
        for (int i = 0; i < code.length(); i++) {
            if (ALPHABET.indexOf(code.charAt(i)) < 0) {
                return false;
            }
        }
        return true;
    }
}

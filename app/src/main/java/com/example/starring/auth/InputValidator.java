package com.example.starring.auth;

import java.util.regex.Pattern;

/**
 * 입력값 검증. 오류가 있으면 한국어 메시지를, 정상이면 null을 반환.
 * (android.util.Patterns는 로컬 유닛 테스트에서 동작하지 않아 정규식을 직접 사용)
 */
public final class InputValidator {

    private InputValidator() {
    }

    public static final int MIN_PASSWORD_LENGTH = 6;
    public static final int MAX_NICKNAME_LENGTH = 12;

    private static final Pattern EMAIL =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    public static String validateEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return "이메일을 입력해주세요.";
        }
        if (!EMAIL.matcher(email.trim()).matches()) {
            return "이메일 형식이 올바르지 않아요.";
        }
        return null;
    }

    /** 로그인용: 빈 칸만 확인 (길이 규칙은 서버 판단에 맡김) */
    public static String validateLoginPassword(String password) {
        if (password == null || password.isEmpty()) {
            return "비밀번호를 입력해주세요.";
        }
        return null;
    }

    /** 회원가입용: 빈 칸 + 최소 길이 */
    public static String validateNewPassword(String password) {
        if (password == null || password.isEmpty()) {
            return "비밀번호를 입력해주세요.";
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            return "비밀번호는 " + MIN_PASSWORD_LENGTH + "자 이상이어야 해요.";
        }
        return null;
    }

    public static String validatePasswordConfirm(String password, String confirm) {
        if (confirm == null || confirm.isEmpty()) {
            return "비밀번호를 한 번 더 입력해주세요.";
        }
        if (!confirm.equals(password)) {
            return "비밀번호가 일치하지 않아요.";
        }
        return null;
    }

    /**
     * users 문서 복구용 닉네임. displayName → 이메일 앞부분 → "별지기" 순으로 쓰고,
     * 항상 {@link #validateNickname}을 통과하도록 최대 길이로 자른다.
     */
    public static String recoveryNickname(String displayName, String email) {
        String candidate = displayName == null ? "" : displayName.trim();
        if (candidate.isEmpty() && email != null && email.indexOf('@') > 0) {
            candidate = email.substring(0, email.indexOf('@')).trim();
        }
        if (candidate.isEmpty()) {
            candidate = "별지기";
        }
        return candidate.length() > MAX_NICKNAME_LENGTH
                ? candidate.substring(0, MAX_NICKNAME_LENGTH)
                : candidate;
    }

    public static String validateNickname(String nickname) {
        if (nickname == null || nickname.trim().isEmpty()) {
            return "닉네임을 입력해주세요.";
        }
        if (nickname.trim().length() > MAX_NICKNAME_LENGTH) {
            return "닉네임은 " + MAX_NICKNAME_LENGTH + "자 이하로 입력해주세요.";
        }
        return null;
    }
}

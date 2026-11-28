package com.example.starring.chat;

import androidx.annotation.Nullable;

/**
 * 채팅 메시지 검사 (rules와 같은 기준: text 1~500자).
 * 앞뒤 공백은 잘라서 보낸다.
 * 글자 수는 rules의 string.size()와 같은 UTF-16 길이(String.length) — 에뮬레이터로 확인:
 * 이모지(🌟)는 2자로 세어 250개까지 허용, 251개는 거부.
 */
public final class ChatPolicy {

    public static final int MAX_LENGTH = 500;

    private ChatPolicy() {
    }

    /** 보낼 텍스트 (앞뒤 공백 제거). 보낼 수 없으면 null */
    @Nullable
    public static String normalize(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim();
        if (text.length() < 1 || text.length() > MAX_LENGTH) {
            return null;
        }
        return text;
    }

    /** 보낼 때 세는 글자 수 (앞뒤 공백 제외, rules와 같은 UTF-16 길이) */
    public static int length(@Nullable String raw) {
        return raw == null ? 0 : raw.trim().length();
    }
}

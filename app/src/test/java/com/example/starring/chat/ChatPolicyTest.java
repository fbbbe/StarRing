package com.example.starring.chat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class ChatPolicyTest {

    private static String repeat(String s, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            sb.append(s);
        }
        return sb.toString();
    }

    @Test
    public void emptyOrBlank_rejected() {
        assertNull(ChatPolicy.normalize(null));
        assertNull(ChatPolicy.normalize(""));
        assertNull(ChatPolicy.normalize("   \n\t "));
    }

    @Test
    public void trimsSurroundingWhitespace() {
        assertEquals("안녕", ChatPolicy.normalize("  안녕 \n"));
        assertEquals("a\nb", ChatPolicy.normalize("a\nb"));
    }

    @Test
    public void maxLength_500() {
        assertEquals(500, ChatPolicy.normalize(repeat("가", 500)).length());
        assertNull(ChatPolicy.normalize(repeat("가", 501)));
        // 공백을 잘라낸 뒤 500자면 허용
        assertEquals(500, ChatPolicy.normalize("  " + repeat("a", 500) + "  ").length());
    }

    @Test
    public void emojiCountsAsTwo_sameAsRules() {
        // rules string.size()와 같은 기준 (에뮬레이터: 이모지 250개 허용, 251개 거부)
        String star = "🌟"; // 🌟 (UTF-16 두 칸)
        assertEquals(2, ChatPolicy.length(star));
        assertEquals(500, ChatPolicy.normalize(repeat(star, 250)).length());
        assertNull(ChatPolicy.normalize(repeat(star, 251)));
        assertNull(ChatPolicy.normalize(repeat(star, 250) + "가"));
    }
}

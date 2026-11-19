package com.example.starring.couple;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class InviteCodeInputFilterTest {

    private final InviteCodeInputFilter filter = new InviteCodeInputFilter();

    private CharSequence apply(String input) {
        return filter.filter(input, 0, input.length(), null, 0, 0);
    }

    @Test
    public void lowercaseAndSpaces_areCleaned() {
        // 공백/하이픈이 길이 제한(6)에 걸려 잘리던 버그 회귀 방지
        assertEquals("77TNGR", apply("77t ngr").toString());
        assertEquals("ABC23K", apply("ab c-23k").toString());
    }

    @Test
    public void alreadyClean_returnsNull() {
        assertNull(apply("K7M2QX"));
    }

    @Test
    public void nonAscii_removed() {
        assertEquals("AB", apply("가a나b").toString());
    }
}

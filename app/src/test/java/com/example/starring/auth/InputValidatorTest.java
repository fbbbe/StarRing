package com.example.starring.auth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class InputValidatorTest {

    @Test
    public void email() {
        assertNotNull(InputValidator.validateEmail(""));
        assertNotNull(InputValidator.validateEmail("   "));
        assertNotNull(InputValidator.validateEmail("abc"));
        assertNotNull(InputValidator.validateEmail("abc@"));
        assertNotNull(InputValidator.validateEmail("abc@test"));
        assertNull(InputValidator.validateEmail("star.ring+1@test.co.kr"));
    }

    @Test
    public void newPassword() {
        assertNotNull(InputValidator.validateNewPassword(""));
        assertNotNull(InputValidator.validateNewPassword("12345"));
        assertNull(InputValidator.validateNewPassword("123456"));
    }

    @Test
    public void loginPassword_onlyChecksEmpty() {
        assertNotNull(InputValidator.validateLoginPassword(""));
        assertNull(InputValidator.validateLoginPassword("1"));
    }

    @Test
    public void passwordConfirm() {
        assertNotNull(InputValidator.validatePasswordConfirm("123456", ""));
        assertNotNull(InputValidator.validatePasswordConfirm("123456", "123457"));
        assertNull(InputValidator.validatePasswordConfirm("123456", "123456"));
    }

    @Test
    public void recoveryNickname_alwaysPassesValidation() {
        // 이메일 앞부분이 12자를 넘던 경우 규칙 위반으로 복구가 실패하던 버그 회귀 방지
        String n = InputValidator.recoveryNickname(null, "sh.testd162622@starring.dev");
        assertEquals("sh.testd1626", n);
        assertNull(InputValidator.validateNickname(n));
        assertEquals("별지기", InputValidator.recoveryNickname("  ", null));
        assertEquals("rotD", InputValidator.recoveryNickname("rotD", "x@y.com"));
    }

    @Test
    public void nickname() {
        assertNotNull(InputValidator.validateNickname(" "));
        assertNotNull(InputValidator.validateNickname("1234567890123"));
        assertNull(InputValidator.validateNickname("별지기"));
        assertNull(InputValidator.validateNickname("123456789012"));
    }
}

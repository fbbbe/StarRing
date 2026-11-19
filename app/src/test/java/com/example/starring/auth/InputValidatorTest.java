package com.example.starring.auth;

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
    public void nickname() {
        assertNotNull(InputValidator.validateNickname(" "));
        assertNotNull(InputValidator.validateNickname("1234567890123"));
        assertNull(InputValidator.validateNickname("별지기"));
        assertNull(InputValidator.validateNickname("123456789012"));
    }
}

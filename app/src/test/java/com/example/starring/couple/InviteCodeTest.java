package com.example.starring.couple;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Random;
import java.util.regex.Pattern;

public class InviteCodeTest {

    /** firestore.rules의 inviteCodes 정규식과 같아야 함 */
    private static final Pattern RULES_PATTERN = Pattern.compile("^[A-HJ-NP-Z2-9]{6}$");

    @Test
    public void generate_matchesRulesPattern() {
        Random random = new Random(42);
        for (int i = 0; i < 2000; i++) {
            String code = InviteCode.generate(random);
            assertTrue(code, RULES_PATTERN.matcher(code).matches());
            assertTrue(InviteCode.isValid(code));
        }
    }

    @Test
    public void alphabet_excludesConfusingChars() {
        for (char c : "0O1I".toCharArray()) {
            assertEquals(-1, InviteCode.ALPHABET.indexOf(c));
        }
    }

    @Test
    public void normalize() {
        assertEquals("ABC234", InviteCode.normalize(" abc-234 "));
        assertEquals("", InviteCode.normalize(null));
    }

    @Test
    public void validateInviteCode() {
        assertNotNull(CoupleInputValidator.validateInviteCode(""));
        assertNotNull(CoupleInputValidator.validateInviteCode("ABC23"));
        assertNotNull(CoupleInputValidator.validateInviteCode("ABC230")); // 0 사용 불가
        assertFalse(InviteCode.isValid("ABCDEI"));
        assertNull(CoupleInputValidator.validateInviteCode("K7M2QX"));
    }

    @Test
    public void validateGalaxyName() {
        assertNotNull(CoupleInputValidator.validateGalaxyName("  "));
        assertNotNull(CoupleInputValidator.validateGalaxyName("123456789012345678901"));
        assertNull(CoupleInputValidator.validateGalaxyName("수현이네"));
    }
}

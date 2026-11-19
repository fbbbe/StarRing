package com.example.starring.couple;

/** 커플 연동 입력값 검증. 오류면 한국어 메시지, 정상이면 null. */
public final class CoupleInputValidator {

    private CoupleInputValidator() {
    }

    public static final int MAX_GALAXY_NAME_LENGTH = 20;

    /** normalize된 코드를 받는다. */
    public static String validateInviteCode(String code) {
        if (code == null || code.isEmpty()) {
            return "초대코드를 입력해주세요.";
        }
        if (code.length() != InviteCode.LENGTH) {
            return "초대코드는 " + InviteCode.LENGTH + "자리예요.";
        }
        if (!InviteCode.isValid(code)) {
            return "초대코드 형식이 올바르지 않아요. (0, O, 1, I는 쓰지 않아요)";
        }
        return null;
    }

    public static String validateGalaxyName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "은하계 이름을 입력해주세요.";
        }
        if (name.trim().length() > MAX_GALAXY_NAME_LENGTH) {
            return "은하계 이름은 " + MAX_GALAXY_NAME_LENGTH + "자 이하로 입력해주세요.";
        }
        return null;
    }
}

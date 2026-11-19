package com.example.starring.auth;

import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.FirebaseTooManyRequestsException;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.firestore.FirebaseFirestoreException;

/** Firebase 예외 → 사용자에게 보여줄 한국어 메시지. */
public final class AuthErrorMapper {

    private AuthErrorMapper() {
    }

    public static String toMessage(Exception e) {
        if (e instanceof FirebaseNetworkException) {
            return "네트워크 연결을 확인해주세요.";
        }
        if (e instanceof FirebaseTooManyRequestsException) {
            return "요청이 너무 많아요. 잠시 후 다시 시도해주세요.";
        }
        if (e instanceof FirebaseAuthException) {
            return fromAuthCode(((FirebaseAuthException) e).getErrorCode());
        }
        if (e instanceof FirebaseFirestoreException) {
            FirebaseFirestoreException.Code code = ((FirebaseFirestoreException) e).getCode();
            if (code == FirebaseFirestoreException.Code.UNAVAILABLE
                    || code == FirebaseFirestoreException.Code.DEADLINE_EXCEEDED) {
                return "네트워크 연결을 확인해주세요.";
            }
            if (code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                return "접근 권한이 없어요. 다시 로그인해주세요.";
            }
        }
        return "알 수 없는 오류가 발생했어요. 잠시 후 다시 시도해주세요.";
    }

    private static String fromAuthCode(String code) {
        if (code == null) {
            return "인증 중 오류가 발생했어요.";
        }
        switch (code) {
            case "ERROR_EMAIL_ALREADY_IN_USE":
                return "이미 사용 중인 이메일이에요.";
            case "ERROR_WEAK_PASSWORD":
                return "비밀번호는 " + InputValidator.MIN_PASSWORD_LENGTH + "자 이상이어야 해요.";
            case "ERROR_INVALID_EMAIL":
                return "이메일 형식이 올바르지 않아요.";
            // 이메일 열거 보호가 켜진 프로젝트는 아래 세 경우가 모두 INVALID_CREDENTIAL로 온다
            case "ERROR_INVALID_CREDENTIAL":
            case "ERROR_WRONG_PASSWORD":
            case "ERROR_USER_NOT_FOUND":
                return "이메일 또는 비밀번호가 올바르지 않아요.";
            case "ERROR_USER_DISABLED":
                return "사용이 중지된 계정이에요.";
            case "ERROR_TOO_MANY_REQUESTS":
                return "요청이 너무 많아요. 잠시 후 다시 시도해주세요.";
            case "ERROR_NETWORK_REQUEST_FAILED":
                return "네트워크 연결을 확인해주세요.";
            case "ERROR_OPERATION_NOT_ALLOWED":
                return "이메일 로그인이 비활성화되어 있어요. (Firebase 콘솔 설정 확인)";
            default:
                return "인증 중 오류가 발생했어요. (" + code + ")";
        }
    }
}

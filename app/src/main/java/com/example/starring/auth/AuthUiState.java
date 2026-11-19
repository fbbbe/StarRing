package com.example.starring.auth;

import androidx.annotation.Nullable;

/**
 * 로그인/회원가입 화면 상태. ViewModel이 들고 있어서 화면 회전 후에도 유지된다.
 */
public final class AuthUiState {

    public enum Status {
        IDLE,
        LOADING,
        /** 성공 → 다음 화면으로 */
        SUCCESS,
        /** 회원가입: 계정은 생겼지만 users 문서 저장 실패 → Splash에서 복구 */
        SUCCESS_NEEDS_RECOVERY,
        ERROR
    }

    public final Status status;
    @Nullable
    public final String message;

    private AuthUiState(Status status, @Nullable String message) {
        this.status = status;
        this.message = message;
    }

    public static AuthUiState idle() {
        return new AuthUiState(Status.IDLE, null);
    }

    public static AuthUiState loading() {
        return new AuthUiState(Status.LOADING, null);
    }

    public static AuthUiState success() {
        return new AuthUiState(Status.SUCCESS, null);
    }

    public static AuthUiState successNeedsRecovery() {
        return new AuthUiState(Status.SUCCESS_NEEDS_RECOVERY, null);
    }

    public static AuthUiState error(String message) {
        return new AuthUiState(Status.ERROR, message);
    }
}

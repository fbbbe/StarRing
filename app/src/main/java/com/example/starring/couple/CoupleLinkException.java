package com.example.starring.couple;

import androidx.annotation.Nullable;

import com.google.firebase.firestore.FirebaseFirestoreException;

/**
 * 커플 연동 규칙 위반을 나타내는 예외. 트랜잭션 함수 안에서 던진다.
 *
 * 트랜잭션 함수는 FirebaseFirestoreException만 던질 수 있어서 이를 상속한다.
 * 코드를 INVALID_ARGUMENT(영구 오류)로 둔 이유: ABORTED/FAILED_PRECONDITION 등은
 * SDK가 "재시도 가능"으로 보고 트랜잭션을 반복 실행하기 때문.
 */
public class CoupleLinkException extends FirebaseFirestoreException {

    public enum Reason {
        ALREADY_IN_GALAXY("이미 연결된 은하계가 있어요."),
        INVALID_CODE("존재하지 않거나 이미 사용된 초대코드예요. 다시 확인해주세요."),
        OWN_CODE("내가 만든 초대코드로는 합류할 수 없어요."),
        GALAXY_FULL("이미 두 사람이 연결된 은하계예요."),
        CODE_EXPIRED_OR_DENIED("만료되었거나 사용할 수 없는 초대코드예요. 상대에게 새 코드를 요청해주세요."),
        CODE_COLLISION("초대코드 발급에 실패했어요. 다시 시도해주세요."),
        NOT_WAITING("상대가 이미 합류해서 취소할 수 없어요."),
        NOT_NAMING("지금은 은하계 이름을 정할 수 없어요."),
        INVALID_START_DATE("만난 날은 1950년 1월 1일부터 오늘까지 고를 수 있어요."),
        USER_NOT_FOUND("사용자 정보를 찾을 수 없어요. 다시 로그인해주세요.");

        final String message;

        Reason(String message) {
            this.message = message;
        }
    }

    private final Reason reason;

    public CoupleLinkException(Reason reason) {
        super(reason.name(), Code.INVALID_ARGUMENT);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }

    public String getUserMessage() {
        return reason.message;
    }

    /** 원인 체인을 따라가며 CoupleLinkException을 찾는다. 없으면 null. */
    @Nullable
    public static CoupleLinkException find(@Nullable Throwable t) {
        while (t != null) {
            if (t instanceof CoupleLinkException) {
                return (CoupleLinkException) t;
            }
            t = t.getCause();
        }
        return null;
    }

    public static boolean is(@Nullable Throwable t, Reason reason) {
        CoupleLinkException e = find(t);
        return e != null && e.reason == reason;
    }
}

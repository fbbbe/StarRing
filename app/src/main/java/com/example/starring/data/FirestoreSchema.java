package com.example.starring.data;

/**
 * Firestore 컬렉션/필드 이름 상수.
 *
 * users/{uid}
 *   email, nickname, galaxyId(null 가능), createdAt(serverTimestamp)
 *
 * galaxies/{galaxyId}
 *   ownerUid, memberUids[최대 2], inviteCode(대기 중에만), status,
 *   name(생성 시 입력), createdAt, bornAt(연동 후 활성화 시각 = 은하계 탄생), unlinkedAt,
 *   datingStartDate(만난 날, "yyyy-MM-dd" 문자열. 만든 사람이 생성 시 입력.
 *             도입 전 은하계는 활성 상태에서 최초 1회 추가 가능. 값 검증은 couple.StartDatePolicy,
 *             없으면 GalaxyDates.effectiveStart가 bornAt으로 대체)
 *   ※ 연동 해제 시에도 memberUids는 지우지 않는다 (과거 기록 접근 유지).
 *
 * inviteCodes/{6자리 코드}
 *   galaxyId, ownerUid, createdAt
 *   ※ 트랜잭션 안에서는 쿼리가 안 되므로 코드를 문서 ID로 둔다.
 *
 * 별 개수, 별자리, D-day 등 시간으로 계산되는 값은 저장하지 않는다 (datingStartDate 기준 계산, common.GalaxyDates).
 */
public final class FirestoreSchema {

    private FirestoreSchema() {
    }

    // 컬렉션
    public static final String USERS = "users";
    public static final String GALAXIES = "galaxies";
    public static final String INVITE_CODES = "inviteCodes";

    // users 필드
    public static final String USER_EMAIL = "email";
    public static final String USER_NICKNAME = "nickname";
    public static final String USER_GALAXY_ID = "galaxyId";
    public static final String USER_CREATED_AT = "createdAt";

    // galaxies 필드
    public static final String GALAXY_OWNER_UID = "ownerUid";
    public static final String GALAXY_MEMBER_UIDS = "memberUids";
    public static final String GALAXY_INVITE_CODE = "inviteCode";
    public static final String GALAXY_STATUS = "status";
    public static final String GALAXY_NAME = "name";
    public static final String GALAXY_CREATED_AT = "createdAt";
    public static final String GALAXY_BORN_AT = "bornAt";
    public static final String GALAXY_UNLINKED_AT = "unlinkedAt";
    public static final String GALAXY_DATING_START_DATE =
            "datingStartDate";

    // inviteCodes 필드
    public static final String CODE_GALAXY_ID = "galaxyId";
    public static final String CODE_OWNER_UID = "ownerUid";
    public static final String CODE_CREATED_AT = "createdAt";

    // galaxies/{galaxyId}/messages 하위 컬렉션 (커플 채팅) 필드
    public static final String MESSAGES = "messages";
    public static final String MESSAGE_SENDER_UID = "senderUid";
    public static final String MESSAGE_TEXT = "text";
    /** serverTimestamp — 보낸 직후 로컬 스냅샷에서는 null */
    public static final String MESSAGE_CREATED_AT = "createdAt";

    /** galaxies.status 값 */
    public static final class GalaxyStatus {
        private GalaxyStatus() {
        }

        /** 만든 사람 혼자, 초대코드 대기 중 */
        public static final String WAITING = "waiting";
        /** 2명 모임, 이름 설정 대기 */
        public static final String NAMING = "naming";
        /** 이름 설정 완료, 정상 사용 중 */
        public static final String ACTIVE = "active";
        /** 연동 해제됨 */
        public static final String UNLINKED = "unlinked";
    }
}

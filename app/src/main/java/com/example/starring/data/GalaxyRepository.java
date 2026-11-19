package com.example.starring.data;

import com.example.starring.couple.CoupleLinkException;
import com.example.starring.couple.CoupleLinkException.Reason;
import com.example.starring.couple.InviteCode;
import com.example.starring.data.FirestoreSchema.GalaxyStatus;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * galaxies / inviteCodes 접근.
 *
 * 생성·합류·이름설정·해제는 전부 트랜잭션으로 처리한다.
 * (트랜잭션 안에서는 쿼리를 쓸 수 없어서 초대코드를 inviteCodes/{code} 문서 ID로 둔다)
 * 각 트랜잭션은 firestore.rules의 규칙과 1:1로 맞춰져 있으니 함께 수정할 것.
 */
public class GalaxyRepository {

    /** 코드 중복 시 새 코드로 다시 시도하는 최대 횟수 */
    private static final int MAX_CODE_ATTEMPTS = 5;

    private final FirebaseFirestore db;

    public GalaxyRepository() {
        this.db = FirebaseFirestore.getInstance();
    }

    public DocumentReference galaxyRef(String galaxyId) {
        return db.collection(FirestoreSchema.GALAXIES).document(galaxyId);
    }

    public DocumentReference inviteCodeRef(String code) {
        return db.collection(FirestoreSchema.INVITE_CODES).document(code);
    }

    private DocumentReference userRef(String uid) {
        return db.collection(FirestoreSchema.USERS).document(uid);
    }

    public Task<DocumentSnapshot> getGalaxy(String galaxyId) {
        return galaxyRef(galaxyId).get();
    }

    public Task<DocumentSnapshot> getInviteCode(String code) {
        return inviteCodeRef(code).get();
    }

    public ListenerRegistration watchGalaxy(String galaxyId,
                                            EventListener<DocumentSnapshot> listener) {
        return galaxyRef(galaxyId).addSnapshotListener(listener);
    }

    // ───────────────────────── 생성 ─────────────────────────

    /** 새 은하계(waiting) + 초대코드 발급. 성공하면 galaxyId 반환. */
    public Task<String> createGalaxy(String uid) {
        return createGalaxyAttempt(uid, 1);
    }

    private Task<String> createGalaxyAttempt(String uid, int attempt) {
        final String code = InviteCode.generate();
        final DocumentReference galaxyRef = db.collection(FirestoreSchema.GALAXIES).document();
        final DocumentReference codeRef = inviteCodeRef(code);
        final DocumentReference userRef = userRef(uid);

        Task<String> task = db.runTransaction(tx -> {
            // 읽기 (트랜잭션은 모든 읽기가 쓰기보다 먼저)
            DocumentSnapshot user = tx.get(userRef);
            DocumentSnapshot existingCode = tx.get(codeRef);

            if (!user.exists()) {
                throw new CoupleLinkException(Reason.USER_NOT_FOUND);
            }
            if (user.getString(FirestoreSchema.USER_GALAXY_ID) != null) {
                throw new CoupleLinkException(Reason.ALREADY_IN_GALAXY);
            }
            if (existingCode.exists()) {
                throw new CoupleLinkException(Reason.CODE_COLLISION);
            }

            List<String> members = new ArrayList<>();
            members.add(uid);

            Map<String, Object> galaxy = new HashMap<>();
            galaxy.put(FirestoreSchema.GALAXY_OWNER_UID, uid);
            galaxy.put(FirestoreSchema.GALAXY_MEMBER_UIDS, members);
            galaxy.put(FirestoreSchema.GALAXY_INVITE_CODE, code);
            galaxy.put(FirestoreSchema.GALAXY_STATUS, GalaxyStatus.WAITING);
            galaxy.put(FirestoreSchema.GALAXY_NAME, null);
            galaxy.put(FirestoreSchema.GALAXY_CREATED_AT, FieldValue.serverTimestamp());
            galaxy.put(FirestoreSchema.GALAXY_BORN_AT, null);
            galaxy.put(FirestoreSchema.GALAXY_UNLINKED_AT, null);

            tx.set(galaxyRef, galaxy);
            tx.set(codeRef, inviteCodeData(galaxyRef.getId(), uid));
            tx.update(userRef, FirestoreSchema.USER_GALAXY_ID, galaxyRef.getId());
            return galaxyRef.getId();
        });

        return task.continueWithTask(t -> {
            if (!t.isSuccessful()
                    && CoupleLinkException.is(t.getException(), Reason.CODE_COLLISION)
                    && attempt < MAX_CODE_ATTEMPTS) {
                return createGalaxyAttempt(uid, attempt + 1);
            }
            return t;
        });
    }

    // ───────────────────────── 합류 ─────────────────────────

    /**
     * 초대코드로 합류. 성공하면 galaxyId 반환.
     * 한 트랜잭션 안에서 확인 + 쓰기를 모두 해서 동시 합류를 막는다.
     * (두 명이 동시에 합류하면 한 명의 트랜잭션은 재실행되고, 그때 GALAXY_FULL/INVALID_CODE로 실패)
     */
    public Task<String> joinGalaxy(String uid, String normalizedCode) {
        final DocumentReference codeRef = inviteCodeRef(normalizedCode);
        final DocumentReference userRef = userRef(uid);

        Task<String> task = db.runTransaction(tx -> {
            DocumentSnapshot invite = tx.get(codeRef);
            if (!invite.exists()) {
                throw new CoupleLinkException(Reason.INVALID_CODE);
            }
            // 자기 코드 확인을 먼저: 만든 사람은 galaxyId가 이미 있어서 아래 검사에 먼저 걸리기 때문
            if (uid.equals(invite.getString(FirestoreSchema.CODE_OWNER_UID))) {
                throw new CoupleLinkException(Reason.OWN_CODE);
            }
            String galaxyId = invite.getString(FirestoreSchema.CODE_GALAXY_ID);
            if (galaxyId == null) {
                throw new CoupleLinkException(Reason.INVALID_CODE);
            }

            DocumentSnapshot user = tx.get(userRef);
            DocumentReference galaxyRef = galaxyRef(galaxyId);
            DocumentSnapshot galaxy = tx.get(galaxyRef);

            if (!user.exists()) {
                throw new CoupleLinkException(Reason.USER_NOT_FOUND);
            }
            if (user.getString(FirestoreSchema.USER_GALAXY_ID) != null) {
                throw new CoupleLinkException(Reason.ALREADY_IN_GALAXY);
            }
            if (!galaxy.exists()) {
                throw new CoupleLinkException(Reason.INVALID_CODE);
            }

            List<String> members = memberUids(galaxy);
            if (members.contains(uid)) {
                throw new CoupleLinkException(Reason.ALREADY_IN_GALAXY);
            }
            if (!GalaxyStatus.WAITING.equals(galaxy.getString(FirestoreSchema.GALAXY_STATUS))
                    || members.size() >= 2) {
                throw new CoupleLinkException(Reason.GALAXY_FULL);
            }

            List<String> newMembers = new ArrayList<>(members);
            newMembers.add(uid);

            // 합류 완료 → 코드 무효화 (galaxy.inviteCode = null + 코드 문서 삭제)
            tx.update(galaxyRef,
                    FirestoreSchema.GALAXY_MEMBER_UIDS, newMembers,
                    FirestoreSchema.GALAXY_STATUS, GalaxyStatus.NAMING,
                    FirestoreSchema.GALAXY_INVITE_CODE, null);
            tx.delete(codeRef);
            tx.update(userRef, FirestoreSchema.USER_GALAXY_ID, galaxyId);
            return galaxyId;
        });

        // 보안 규칙에서 거부 = 대부분 코드 만료(24시간 초과)
        return task.continueWithTask(t -> {
            if (!t.isSuccessful() && isPermissionDenied(t.getException())) {
                return Tasks.forException(new CoupleLinkException(Reason.CODE_EXPIRED_OR_DENIED));
            }
            return t;
        });
    }

    // ───────────────────────── 코드 재발급 / 대기 취소 ─────────────────────────

    /** 대기 중 은하계의 초대코드를 새로 발급 (만료 시). 새 코드 반환. */
    public Task<String> reissueInviteCode(String uid, String galaxyId) {
        return reissueAttempt(uid, galaxyId, 1);
    }

    private Task<String> reissueAttempt(String uid, String galaxyId, int attempt) {
        final String newCode = InviteCode.generate();
        final DocumentReference galaxyRef = galaxyRef(galaxyId);
        final DocumentReference newCodeRef = inviteCodeRef(newCode);

        Task<String> task = db.runTransaction(tx -> {
            DocumentSnapshot galaxy = tx.get(galaxyRef);
            DocumentSnapshot existing = tx.get(newCodeRef);

            if (!galaxy.exists()
                    || !uid.equals(galaxy.getString(FirestoreSchema.GALAXY_OWNER_UID))
                    || !GalaxyStatus.WAITING.equals(galaxy.getString(FirestoreSchema.GALAXY_STATUS))) {
                throw new CoupleLinkException(Reason.NOT_WAITING);
            }
            if (existing.exists()) {
                throw new CoupleLinkException(Reason.CODE_COLLISION);
            }

            String oldCode = galaxy.getString(FirestoreSchema.GALAXY_INVITE_CODE);
            if (oldCode != null) {
                tx.delete(inviteCodeRef(oldCode));
            }
            tx.set(newCodeRef, inviteCodeData(galaxyId, uid));
            tx.update(galaxyRef, FirestoreSchema.GALAXY_INVITE_CODE, newCode);
            return newCode;
        });

        return task.continueWithTask(t -> {
            if (!t.isSuccessful()
                    && CoupleLinkException.is(t.getException(), Reason.CODE_COLLISION)
                    && attempt < MAX_CODE_ATTEMPTS) {
                return reissueAttempt(uid, galaxyId, attempt + 1);
            }
            return t;
        });
    }

    /** 대기 취소: 코드·은하계 삭제 + 내 galaxyId 비우기. 상대가 이미 합류했으면 NOT_WAITING. */
    public Task<Void> cancelWaiting(String uid, String galaxyId) {
        final DocumentReference galaxyRef = galaxyRef(galaxyId);
        final DocumentReference userRef = userRef(uid);

        return db.runTransaction(tx -> {
            DocumentSnapshot galaxy = tx.get(galaxyRef);

            if (galaxy.exists()) {
                if (!GalaxyStatus.WAITING.equals(galaxy.getString(FirestoreSchema.GALAXY_STATUS))
                        || !uid.equals(galaxy.getString(FirestoreSchema.GALAXY_OWNER_UID))) {
                    throw new CoupleLinkException(Reason.NOT_WAITING);
                }
                String code = galaxy.getString(FirestoreSchema.GALAXY_INVITE_CODE);
                if (code != null) {
                    tx.delete(inviteCodeRef(code));
                }
                tx.delete(galaxyRef);
            }
            tx.update(userRef, FirestoreSchema.USER_GALAXY_ID, null);
            return null;
        });
    }

    // ───────────────────────── 이름 설정 ─────────────────────────

    /**
     * 이름 설정 → active. bornAt(은하계 탄생 시각)은 서버 시각으로 기록.
     * 상대가 먼저 이름을 정했으면(이미 active) 아무것도 하지 않고 성공 처리 → 먼저 저장한 사람의 이름이 적용됨.
     */
    public Task<Void> nameGalaxy(String uid, String galaxyId, String name) {
        final DocumentReference galaxyRef = galaxyRef(galaxyId);

        return db.runTransaction(tx -> {
            DocumentSnapshot galaxy = tx.get(galaxyRef);
            String status = galaxy.getString(FirestoreSchema.GALAXY_STATUS);

            if (GalaxyStatus.ACTIVE.equals(status)) {
                return null;
            }
            if (!galaxy.exists() || !GalaxyStatus.NAMING.equals(status)
                    || !memberUids(galaxy).contains(uid)) {
                throw new CoupleLinkException(Reason.NOT_NAMING);
            }
            tx.update(galaxyRef,
                    FirestoreSchema.GALAXY_NAME, name,
                    FirestoreSchema.GALAXY_STATUS, GalaxyStatus.ACTIVE,
                    FirestoreSchema.GALAXY_BORN_AT, FieldValue.serverTimestamp());
            return null;
        });
    }

    // ───────────────────────── 연동 해제 ─────────────────────────

    /**
     * 연동 해제 (헤어졌을 때).
     *
     * ⚠ memberUids에서 나를 제거하지 않는다 — 의도된 동작.
     *   해제 후에도 두 사람 모두 이 은하계의 과거 기록(행성 사진 등)에 접근할 수 있도록
     *   멤버 목록은 그대로 두고 status = unlinked, unlinkedAt만 기록한다.
     *   (firestore.rules도 해제 시 memberUids 변경을 막고 있음)
     *
     * 상대의 users 문서는 수정하지 않는다 → 상대 앱이 unlinked를 감지해서 스스로 galaxyId를 비움.
     */
    public Task<Void> unlink(String uid, String galaxyId) {
        final DocumentReference galaxyRef = galaxyRef(galaxyId);
        final DocumentReference userRef = userRef(uid);

        return db.runTransaction(tx -> {
            DocumentSnapshot galaxy = tx.get(galaxyRef);
            String status = galaxy.getString(FirestoreSchema.GALAXY_STATUS);

            boolean linked = GalaxyStatus.NAMING.equals(status) || GalaxyStatus.ACTIVE.equals(status);
            if (galaxy.exists() && linked && memberUids(galaxy).contains(uid)) {
                tx.update(galaxyRef,
                        FirestoreSchema.GALAXY_STATUS, GalaxyStatus.UNLINKED,
                        FirestoreSchema.GALAXY_UNLINKED_AT, FieldValue.serverTimestamp());
            }
            // 이미 상대가 해제했으면(unlinked) 내 문서만 정리
            tx.update(userRef, FirestoreSchema.USER_GALAXY_ID, null);
            return null;
        });
    }

    // ───────────────────────── 공통 ─────────────────────────

    private static Map<String, Object> inviteCodeData(String galaxyId, String ownerUid) {
        Map<String, Object> data = new HashMap<>();
        data.put(FirestoreSchema.CODE_GALAXY_ID, galaxyId);
        data.put(FirestoreSchema.CODE_OWNER_UID, ownerUid);
        data.put(FirestoreSchema.CODE_CREATED_AT, FieldValue.serverTimestamp());
        return data;
    }

    @SuppressWarnings("unchecked")
    public static List<String> memberUids(DocumentSnapshot galaxy) {
        Object value = galaxy.get(FirestoreSchema.GALAXY_MEMBER_UIDS);
        if (value instanceof List) {
            return (List<String>) value;
        }
        return new ArrayList<>();
    }

    private static boolean isPermissionDenied(Exception e) {
        Throwable t = e;
        while (t != null) {
            if (t instanceof FirebaseFirestoreException
                    && ((FirebaseFirestoreException) t).getCode()
                    == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }
}

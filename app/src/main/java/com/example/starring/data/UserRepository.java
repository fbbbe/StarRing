package com.example.starring.data;

import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

/** users 컬렉션 접근. */
public class UserRepository {

    private final FirebaseFirestore db;

    public UserRepository() {
        this.db = FirebaseFirestore.getInstance();
    }

    public DocumentReference userRef(String uid) {
        return db.collection(FirestoreSchema.USERS).document(uid);
    }

    /** 회원가입 직후 사용자 문서 생성. galaxyId는 null로 시작. */
    public Task<Void> createUser(String uid, String email, String nickname) {
        Map<String, Object> data = new HashMap<>();
        data.put(FirestoreSchema.USER_EMAIL, email);
        data.put(FirestoreSchema.USER_NICKNAME, nickname);
        data.put(FirestoreSchema.USER_GALAXY_ID, null);
        data.put(FirestoreSchema.USER_CREATED_AT, FieldValue.serverTimestamp());
        return userRef(uid).set(data);
    }

    public Task<DocumentSnapshot> getUser(String uid) {
        return userRef(uid).get();
    }

    /** 내 문서의 galaxyId만 비운다 (연동 해제/삭제된 은하계 정리용). 상대 문서는 건드리지 않는다. */
    public Task<Void> clearGalaxyId(String uid) {
        return userRef(uid).update(FirestoreSchema.USER_GALAXY_ID, null);
    }
}

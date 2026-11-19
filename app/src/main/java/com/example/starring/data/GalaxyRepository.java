package com.example.starring.data;

import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

/** galaxies 컬렉션 접근. (생성/합류/해제 트랜잭션은 2단계에서 추가) */
public class GalaxyRepository {

    private final FirebaseFirestore db;

    public GalaxyRepository() {
        this.db = FirebaseFirestore.getInstance();
    }

    public DocumentReference galaxyRef(String galaxyId) {
        return db.collection(FirestoreSchema.GALAXIES).document(galaxyId);
    }

    public Task<DocumentSnapshot> getGalaxy(String galaxyId) {
        return galaxyRef(galaxyId).get();
    }
}

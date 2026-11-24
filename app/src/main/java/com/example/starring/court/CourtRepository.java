
package com.example.starring.court;

import android.util.Log;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CourtRepository {

    private static final String TAG = "CourtRepository";
    private static final String COLLECTION = "courtPosts";

    private static final long VOTE_DURATION =
            24L * 60L * 60L * 1000L;

    private static final FirebaseFirestore db =
            FirebaseFirestore.getInstance();

    // Firestore에서 받아온 데이터 캐시
    private static final Map<String, CourtPost> POSTS =
            new HashMap<>();

    private static final Map<String, CourtPost> MY_POSTS =
            new HashMap<>();

    private static final List<Runnable> observers =
            new ArrayList<>();

    private static ListenerRegistration postsListener;
    private static ListenerRegistration userListener;
    private static ListenerRegistration myPostsListener;

    private static String currentUid;
    private static String currentGalaxyId;

    private CourtRepository() {
    }

    // ==========================================
    // 로그인 사용자 확인
    // ==========================================

    private static FirebaseUser requireUser() {
        FirebaseUser user =
                FirebaseAuth.getInstance().getCurrentUser();

        if (user == null) {
            throw new IllegalStateException(
                    "로그인이 필요합니다."
            );
        }

        return user;
    }

    private static DocumentReference postRef(String id) {
        return db.collection(COLLECTION).document(id);
    }

    // ==========================================
    // Firestore 실시간 조회 시작
    // ==========================================

    private static synchronized void ensureListening() {

        FirebaseUser user =
                FirebaseAuth.getInstance().getCurrentUser();

        if (user == null) {
            stopListening();
            return;
        }

        String uid = user.getUid();

        if (uid.equals(currentUid)
                && postsListener != null) {
            return;
        }

        stopListening();

        currentUid = uid;

        // 전체 재판 목록 조회
        postsListener = db.collection(COLLECTION)
                .orderBy(
                        "createdAt",
                        Query.Direction.DESCENDING
                )
                .limit(50)
                .addSnapshotListener((snapshot, error) -> {

                    if (error != null) {
                        Log.e(TAG,
                                "재판 목록 조회 실패",
                                error);
                        return;
                    }

                    if (snapshot == null) {
                        return;
                    }

                    synchronized (CourtRepository.class) {
                        POSTS.clear();

                        for (DocumentSnapshot doc :
                                snapshot.getDocuments()) {

                            CourtPost post = fromDocument(doc);
                            POSTS.put(post.id, post);
                        }
                    }

                    notifyObservers();
                });

        // 현재 사용자의 은하계 ID 실시간 확인
        userListener = db.collection("users")
                .document(uid)
                .addSnapshotListener((snapshot, error) -> {

                    if (error != null || snapshot == null) {
                        return;
                    }

                    if (!uid.equals(currentUid)) {
                        return;
                    }

                    String galaxyId =
                            snapshot.getString("galaxyId");

                    if (galaxyId == null
                            ? currentGalaxyId == null
                            : galaxyId.equals(currentGalaxyId)) {
                        return;
                    }

                    currentGalaxyId = galaxyId;

                    if (myPostsListener != null) {
                        myPostsListener.remove();
                        myPostsListener = null;
                    }

                    MY_POSTS.clear();

                    if (galaxyId == null) {
                        notifyObservers();
                        return;
                    }

                    // 현재 커플이 생성한 블랙홀 조회
                    myPostsListener = db.collection(COLLECTION)
                            .whereEqualTo(
                                    "galaxyId",
                                    galaxyId
                            )
                            .limit(50)
                            .addSnapshotListener(
                                    (postsSnapshot, e) -> {

                                        if (e != null) {
                                            Log.e(TAG,
                                                    "내 블랙홀 조회 실패",
                                                    e);
                                            return;
                                        }

                                        if (postsSnapshot == null) {
                                            return;
                                        }

                                        synchronized (
                                                CourtRepository.class) {

                                            MY_POSTS.clear();

                                            for (DocumentSnapshot doc :
                                                    postsSnapshot.getDocuments()) {

                                                CourtPost post =
                                                        fromDocument(doc);

                                                post.mine = true;
                                                MY_POSTS.put(
                                                        post.id, post
                                                );
                                            }
                                        }

                                        notifyObservers();
                                    });
                });
    }

    public static synchronized void stopListening() {

        if (postsListener != null) {
            postsListener.remove();
            postsListener = null;
        }

        if (userListener != null) {
            userListener.remove();
            userListener = null;
        }

        if (myPostsListener != null) {
            myPostsListener.remove();
            myPostsListener = null;
        }

        POSTS.clear();
        MY_POSTS.clear();

        currentUid = null;
        currentGalaxyId = null;
    }

    // ==========================================
    // 화면에 데이터 변경 알림
    // ==========================================

    public static synchronized void addObserver(
            Runnable observer) {

        if (observer != null
                && !observers.contains(observer)) {
            observers.add(observer);
        }

        ensureListening();
    }

    public static synchronized void removeObserver(
            Runnable observer) {
        observers.remove(observer);
    }

    private static void notifyObservers() {

        List<Runnable> copy;

        synchronized (CourtRepository.class) {
            copy = new ArrayList<>(observers);
        }

        for (Runnable observer : copy) {
            observer.run();
        }
    }

    // ==========================================
    // Firestore 문서 -> CourtPost
    // ==========================================

    private static CourtPost fromDocument(
            DocumentSnapshot doc) {

        CourtPost post = new CourtPost();

        post.id = doc.getId();

        post.title = doc.getString("title");
        post.situation = doc.getString("situation");
        post.positionA = doc.getString("positionA");
        post.positionB = doc.getString("positionB");
        post.galaxyName = doc.getString("galaxyName");

        Long votesA = doc.getLong("votesA");
        Long votesB = doc.getLong("votesB");

        post.votesA = votesA == null
                ? 0 : votesA.intValue();

        post.votesB = votesB == null
                ? 0 : votesB.intValue();

        post.createdAt =
                timestampToMillis(doc, "createdAt");

        post.deadlineAt =
                timestampToMillis(doc, "deadlineAt");

        post.resolvedAt =
                timestampToMillis(doc, "resolvedAt");

        String galaxyId = doc.getString("galaxyId");

        post.mine = currentGalaxyId != null
                && currentGalaxyId.equals(galaxyId);

        // 기존 캐시의 사용자별 화면 상태 유지
        CourtPost old = MY_POSTS.get(post.id);

        if (old == null) {
            old = POSTS.get(post.id);
        }

        if (old != null) {
            post.myVote = old.myVote;
            post.myLesson = old.myLesson;
            post.partnerLesson = old.partnerLesson;
        }

        return post;
    }

    private static long timestampToMillis(
            DocumentSnapshot doc,
            String field) {

        Timestamp value = doc.getTimestamp(field);

        return value == null
                ? 0L
                : value.toDate().getTime();
    }

    // ==========================================
    // 전체 재판 목록
    // ==========================================

    public static synchronized List<CourtPost> getPosts(
            boolean latestFirst) {

        ensureListening();

        Map<String, CourtPost> merged =
                new HashMap<>(POSTS);

        merged.putAll(MY_POSTS);

        List<CourtPost> result =
                new ArrayList<>(merged.values());

        if (latestFirst) {

            Collections.sort(result,
                    (a, b) -> Long.compare(
                            b.createdAt, a.createdAt));

        } else {

            Collections.sort(result,
                    (a, b) -> Long.compare(
                            a.deadlineAt, b.deadlineAt));
        }

        return result;
    }

    // ==========================================
    // 내가 속한 은하계의 블랙홀
    // ==========================================

    public static synchronized List<CourtPost> getMyPosts(
            boolean resolved) {

        ensureListening();

        List<CourtPost> result = new ArrayList<>();

        for (CourtPost post : MY_POSTS.values()) {

            if (post.isResolved() == resolved) {
                result.add(post);
            }
        }

        result.sort(
                (a, b) -> Long.compare(
                        b.createdAt, a.createdAt)
        );

        return result;
    }

    public static synchronized boolean hasOpenBlackhole() {

        ensureListening();

        for (CourtPost post : MY_POSTS.values()) {
            if (!post.isResolved()) {
                return true;
            }
        }

        return false;
    }

    // ==========================================
    // 재판 단건 조회
    // ==========================================

    public static synchronized CourtPost findById(
            String id) {

        ensureListening();

        if (id == null) {
            return null;
        }

        CourtPost post = MY_POSTS.get(id);

        if (post != null) {
            return post;
        }

        return POSTS.get(id);
    }

    // ==========================================
    // 재판 생성
    // ==========================================

    public static Task<Void> add(CourtPost post) {

        final FirebaseUser user;

        try {
            user = requireUser();
        } catch (Exception e) {
            return Tasks.forException(e);
        }

        String uid = user.getUid();

        if (post == null
                || post.title == null
                || post.situation == null
                || post.positionA == null
                || post.positionB == null) {

            return Tasks.forException(
                    new IllegalArgumentException(
                            "재판 내용을 확인해주세요."
                    )
            );
        }

        // 현재 로그인 사용자의 은하계 조회
        return db.collection("users")
                .document(uid)
                .get()
                .continueWithTask(userTask -> {

                    if (!userTask.isSuccessful()) {
                        return Tasks.forException(
                                userTask.getException()
                        );
                    }

                    String galaxyId =
                            userTask.getResult()
                                    .getString("galaxyId");

                    if (galaxyId == null) {
                        return Tasks.forException(
                                new IllegalStateException(
                                        "연결된 은하계가 없습니다."
                                )
                        );
                    }

                    // 실제 은하계 정보 조회
                    return db.collection("galaxies")
                            .document(galaxyId)
                            .get()
                            .continueWithTask(galaxyTask -> {

                                if (!galaxyTask.isSuccessful()) {
                                    return Tasks.forException(
                                            galaxyTask.getException()
                                    );
                                }

                                DocumentSnapshot galaxy =
                                        galaxyTask.getResult();

                                if (!galaxy.exists()
                                        || !"active".equals(
                                        galaxy.getString("status"))) {

                                    return Tasks.forException(
                                            new IllegalStateException(
                                                    "활성화된 은하계가 없습니다."
                                            )
                                    );
                                }

                                List<String> members =
                                        (List<String>) galaxy.get(
                                                "memberUids"
                                        );

                                if (members == null
                                        || !members.contains(uid)) {

                                    return Tasks.forException(
                                            new IllegalStateException(
                                                    "은하계 구성원이 아닙니다."
                                            )
                                    );
                                }

                                DocumentReference ref =
                                        db.collection(COLLECTION)
                                                .document();

                                Map<String, Object> data =
                                        new HashMap<>();

                                data.put("title",
                                        post.title.trim());

                                data.put("situation",
                                        post.situation.trim());

                                data.put("positionA",
                                        post.positionA.trim());

                                data.put("positionB",
                                        post.positionB.trim());

                                data.put("authorUid", uid);
                                data.put("galaxyId", galaxyId);

                                data.put(
                                        "galaxyName",
                                        galaxy.getString("name")
                                );

                                data.put("memberUids", members);

                                data.put("votesA", 0);
                                data.put("votesB", 0);

                                data.put(
                                        "createdAt",
                                        FieldValue.serverTimestamp()
                                );

                                data.put(
                                        "deadlineAt",
                                        new Timestamp(
                                                new Date(
                                                        System.currentTimeMillis()
                                                                + VOTE_DURATION
                                                )
                                        )
                                );

                                data.put("resolvedAt", null);

                                return ref.set(data);
                            });
                });
    }

    // ==========================================
    // A/B 투표
    // ==========================================

    public static Task<Void> vote(
            String postId,
            int choice) {

        final FirebaseUser user;

        try {
            user = requireUser();
        } catch (Exception e) {
            return Tasks.forException(e);
        }

        if (choice != CourtPost.VOTE_A
                && choice != CourtPost.VOTE_B) {

            return Tasks.forException(
                    new IllegalArgumentException(
                            "잘못된 투표 선택입니다."
                    )
            );
        }

        String uid = user.getUid();

        DocumentReference postRef =
                postRef(postId);

        DocumentReference voteRef =
                postRef.collection("votes")
                        .document(uid);

        return db.runTransaction(transaction -> {

            // 트랜잭션은 읽기를 먼저 수행
            DocumentSnapshot post =
                    transaction.get(postRef);

            DocumentSnapshot previous =
                    transaction.get(voteRef);

            if (!post.exists()) {
                throw new IllegalStateException(
                        "재판을 찾을 수 없습니다."
                );
            }

            if (previous.exists()) {
                throw new IllegalStateException(
                        "이미 투표한 재판입니다."
                );
            }

            List<String> members =
                    (List<String>) post.get("memberUids");

            if (members != null && members.contains(uid)) {
                throw new IllegalStateException(
                        "재판 당사자는 투표할 수 없습니다."
                );
            }

            Timestamp deadline =
                    post.getTimestamp("deadlineAt");

            if (deadline == null
                    || System.currentTimeMillis()
                    >= deadline.toDate().getTime()
                    || post.getTimestamp("resolvedAt") != null) {

                throw new IllegalStateException(
                        "투표가 마감되었습니다."
                );
            }

            String voteField =
                    choice == CourtPost.VOTE_A
                            ? "votesA" : "votesB";

            Long current =
                    post.getLong(voteField);

            Map<String, Object> voteData =
                    new HashMap<>();

            voteData.put("choice", choice);

            voteData.put(
                    "createdAt",
                    FieldValue.serverTimestamp()
            );

            // 투표 기록 저장
            transaction.set(voteRef, voteData);

            // 투표 집계 증가
            transaction.update(
                    postRef,
                    voteField,
                    (current == null ? 0L : current) + 1L
            );

            return null;
        }).continueWith(task -> {

            if (!task.isSuccessful()) {
                throw task.getException();
            }

            synchronized (CourtRepository.class) {
                CourtPost cached = POSTS.get(postId);
                if (cached != null) {
                    cached.myVote = choice;
                }

                CourtPost mine = MY_POSTS.get(postId);
                if (mine != null) {
                    mine.myVote = choice;
                }
            }

            notifyObservers();
            return null;
        });
    }

    // ==========================================
    // 화해: 내 배운 점 작성
    // ==========================================

    public static Task<Void> submitMyLesson(
            String postId,
            String lesson) {

        final FirebaseUser user;

        try {
            user = requireUser();
        } catch (Exception e) {
            return Tasks.forException(e);
        }

        String text = lesson == null
                ? "" : lesson.trim();

        if (text.isEmpty() || text.length() > 2000) {
            return Tasks.forException(
                    new IllegalArgumentException(
                            "배운 점은 1~2000자로 입력해주세요."
                    )
            );
        }

        String uid = user.getUid();

        DocumentReference postRef =
                postRef(postId);

        DocumentReference lessonRef =
                postRef.collection("lessons")
                        .document(uid);

        return db.runTransaction(transaction -> {

            DocumentSnapshot post =
                    transaction.get(postRef);

            DocumentSnapshot previous =
                    transaction.get(lessonRef);

            if (!post.exists()) {
                throw new IllegalStateException(
                        "재판이 존재하지 않습니다."
                );
            }

            if (post.getTimestamp("resolvedAt") != null) {
                throw new IllegalStateException(
                        "이미 화해한 재판입니다."
                );
            }

            List<String> members =
                    (List<String>) post.get("memberUids");

            if (members == null
                    || !members.contains(uid)) {
                throw new IllegalStateException(
                        "재판 당사자만 작성할 수 있습니다."
                );
            }

            if (previous.exists()) {
                throw new IllegalStateException(
                        "이미 배운 점을 작성했습니다."
                );
            }

            Map<String, Object> data =
                    new HashMap<>();

            data.put("lesson", text);
            data.put(
                    "createdAt",
                    FieldValue.serverTimestamp()
            );

            transaction.set(lessonRef, data);

            return null;


        }).continueWithTask(task -> {

            // 배운 점 저장 자체가 실패한 경우
            if (!task.isSuccessful()) {
                Exception error = task.getException();

                return Tasks.forException(
                        error != null
                                ? error
                                : new IllegalStateException(
                                "배운 점 저장에 실패했습니다."
                        )
                );
            }

            // 저장은 성공했으므로 화해 완료 여부는 별도로 확인
            resolveIfBothWrote(postId)
                    .addOnFailureListener(error -> {
                        Log.w(
                                TAG,
                                "배운 점 저장 성공, 화해 상태 갱신 실패",
                                error
                        );
                    });

            // 화면에는 배운 점 저장 성공을 반환
            return Tasks.<Void>forResult(null);
        });

    }

    // ==========================================
    // 화해 완료 여부 확인
    // ==========================================

    private static Task<Void> resolveIfBothWrote(
            String postId) {

        DocumentReference ref = postRef(postId);

        return db.runTransaction(transaction -> {

            DocumentSnapshot post =
                    transaction.get(ref);

            if (!post.exists()) {
                return null;
            }

            if (post.getTimestamp("resolvedAt") != null) {
                return null;
            }

            List<String> members =
                    (List<String>) post.get("memberUids");

            if (members == null || members.size() != 2) {
                return null;
            }

            DocumentReference lessonA =
                    ref.collection("lessons")
                            .document(members.get(0));

            DocumentReference lessonB =
                    ref.collection("lessons")
                            .document(members.get(1));

            DocumentSnapshot docA =
                    transaction.get(lessonA);

            DocumentSnapshot docB =
                    transaction.get(lessonB);

            if (docA.exists() && docB.exists()) {

                transaction.update(
                        ref,
                        "resolvedAt",
                        FieldValue.serverTimestamp()
                );
            }

            return null;
        });
    }

    // ==========================================
    // 화해 내용 다시 불러오기
    // ==========================================

    public static Task<Void> loadLessons(
            String postId) {

        FirebaseUser user =
                FirebaseAuth.getInstance().getCurrentUser();

        if (user == null) {
            return Tasks.forException(
                    new IllegalStateException(
                            "로그인이 필요합니다."
                    )
            );
        }

        String uid = user.getUid();

        return postRef(postId).get()
                .continueWithTask(postTask -> {

                    if (!postTask.isSuccessful()) {
                        return Tasks.forException(
                                postTask.getException()
                        );
                    }

                    DocumentSnapshot doc =
                            postTask.getResult();

                    List<String> members =
                            (List<String>) doc.get(
                                    "memberUids"
                            );

                    if (members == null
                            || !members.contains(uid)
                            || members.size() != 2) {

                        return Tasks.forException(
                                new IllegalStateException(
                                        "화해 내용을 조회할 수 없습니다."
                                )
                        );
                    }

                    String partnerUid =
                            members.get(0).equals(uid)
                                    ? members.get(1)
                                    : members.get(0);

                    Task<DocumentSnapshot> mine =
                            postRef(postId)
                                    .collection("lessons")
                                    .document(uid)
                                    .get();

                    Task<DocumentSnapshot> partner =
                            postRef(postId)
                                    .collection("lessons")
                                    .document(partnerUid)
                                    .get();

                    return Tasks.whenAllSuccess(mine, partner)
                            .continueWith(task -> {

                                List<Object> results =
                                        task.getResult();

                                DocumentSnapshot myDoc =
                                        (DocumentSnapshot)
                                                results.get(0);

                                DocumentSnapshot partnerDoc =
                                        (DocumentSnapshot)
                                                results.get(1);

                                synchronized (
                                        CourtRepository.class) {

                                    CourtPost post =
                                            MY_POSTS.get(postId);

                                    if (post == null) {
                                        post = POSTS.get(postId);
                                    }

                                    if (post != null) {
                                        post.myLesson =
                                                myDoc.getString("lesson");

                                        post.partnerLesson =
                                                partnerDoc.getString("lesson");
                                    }
                                }

                                notifyObservers();

                                return null;
                            });
                });
    }

    // ==========================================
    // 기존 테스트 메서드
    // ==========================================

    // Firebase에서는 다른 사용자 대신
    // 화해 내용을 작성할 수 없어야 함.
    @Deprecated
    public static void submitPartnerLessonForTest(
            String postId) {

        Log.w(TAG,
                "테스트용 상대 배운 점 제출은 비활성화됨");
    }
}

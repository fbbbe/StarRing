
package com.example.starring.court;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import java.util.Calendar;
import java.util.TimeZone;

import androidx.appcompat.app.AppCompatActivity;

import com.example.starring.R;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CourtReconcileActivity extends AppCompatActivity {

    public static final String EXTRA_POST_ID = "post_id";

    // 임시값: 추후 실제 은하계 애기별 수로 교체
    private int availableStars = 0;
    private String starsGalaxyId;

    private final FirebaseFirestore db =
            FirebaseFirestore.getInstance();

    private final Handler handler =
            new Handler(Looper.getMainLooper());

    private String postId;
    private String uid;
    private String partnerUid;

    private CourtPost currentPost;

    private DocumentReference postRef;

    private ListenerRegistration postListener;
    private ListenerRegistration lessonsListener;

    private final Map<String, String> lessonMap =
            new HashMap<>();

    private boolean receivedFirstPost = false;
    private boolean wasResolved = false;
    private boolean saving = false;
    private boolean submittedLocally = false;
    private boolean resolving = false;

    private TextView textTitle;
    private TextView textSwallowedLabel;
    private TextView textSwallowed;
    private TextView textNext;
    private TextView textGuide;

    private TextView textWaitingMine;
    private TextView textDoneMine;
    private TextView textDonePartner;

    private View layoutInput;
    private View layoutWaiting;
    private View layoutDone;

    private View btnSubmit;
    private View btnPartnerTest;

    private EditText editLesson;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {

            render();

            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_court_reconcile);

        postId = getIntent().getStringExtra(EXTRA_POST_ID);

        FirebaseUser user =
                FirebaseAuth.getInstance().getCurrentUser();

        if (postId == null || user == null) {
            finish();
            return;
        }

        uid = user.getUid();

        postRef = db.collection("courtPosts")
                .document(postId);

        textTitle = findViewById(R.id.textTitle);

        textSwallowedLabel =
                findViewById(R.id.textSwallowedLabel);

        textSwallowed =
                findViewById(R.id.textSwallowed);

        textNext = findViewById(R.id.textNext);
        textGuide = findViewById(R.id.textGuide);

        layoutInput = findViewById(R.id.layoutInput);
        layoutWaiting = findViewById(R.id.layoutWaiting);
        layoutDone = findViewById(R.id.layoutDone);

        editLesson = findViewById(R.id.editLesson);

        textWaitingMine =
                findViewById(R.id.textWaitingMine);

        textDoneMine = findViewById(R.id.textDoneMine);

        textDonePartner =
                findViewById(R.id.textDonePartner);

        btnSubmit = findViewById(R.id.btnSubmit);
        btnPartnerTest = findViewById(R.id.btnPartnerTest);

        findViewById(R.id.btnBack)
                .setOnClickListener(v -> finish());

        btnSubmit.setOnClickListener(v -> submit());

        // 기존 상대방 대신 작성하는 테스트 기능 제거
        if (btnPartnerTest != null) {
            btnPartnerTest.setVisibility(View.GONE);
        }
    }

    // ==========================================
    // Firebase 실시간 조회
    // ==========================================

    @Override
    protected void onStart() {
        super.onStart();

        if (postRef == null || uid == null) {
            return;
        }

        startWatching();
    }

    private void startWatching() {

        stopWatching();

        // 1. 재판 상태 감시
        postListener = postRef.addSnapshotListener(
                (snapshot, error) -> {

                    if (error != null) {
                        CourtNotice.show(
                                this,
                                "재판 조회 실패: " + error.getMessage()
                        );
                        return;
                    }

                    if (snapshot == null) {
                        return;
                    }

                    if (!snapshot.exists()) {
                        CourtNotice.show(
                                this,
                                "재판을 찾을 수 없습니다."
                        );
                        finish();
                        return;
                    }

                    currentPost = readPost(snapshot);
                    loadAvailableStars(snapshot.getString("galaxyId"));

                    if (!receivedFirstPost) {
                        wasResolved = currentPost.isResolved();
                        receivedFirstPost = true;
                    }

                    render();
                }
        );

        // 2. 커플 두 사람의 배운 점 실시간 감시
        lessonsListener = postRef.collection("lessons")
                .addSnapshotListener((snapshot, error) -> {

                    if (error != null) {
                        CourtNotice.show(
                                this,
                                "화해 내용 조회 실패: "
                                        + error.getMessage()
                        );
                        return;
                    }

                    if (snapshot == null) {
                        return;
                    }

                    lessonMap.clear();

                    for (DocumentSnapshot doc :
                            snapshot.getDocuments()) {

                        String lesson =
                                doc.getString("lesson");

                        if (lesson != null) {
                            lessonMap.put(doc.getId(), lesson);
                        }
                    }

                    if (lessonMap.containsKey(uid)) {
                        submittedLocally = false;
                    }

                    render();
                    tryResolveIfBothWrote();
                });
    }

    private void stopWatching() {

        if (postListener != null) {
            postListener.remove();
            postListener = null;
        }

        if (lessonsListener != null) {
            lessonsListener.remove();
            lessonsListener = null;
        }
    }

    // ==========================================
    // Firestore 문서 변환
    // ==========================================

    private CourtPost readPost(DocumentSnapshot doc) {

        CourtPost post = new CourtPost();

        post.id = doc.getId();
        post.title = doc.getString("title");
        post.situation = doc.getString("situation");

        post.createdAt = timeOf(doc, "createdAt");
        post.deadlineAt = timeOf(doc, "deadlineAt");
        post.resolvedAt = timeOf(doc, "resolvedAt");

        partnerUid = null;

        Object memberData = doc.get("memberUids");

        if (memberData instanceof List) {

            List<?> members = (List<?>) memberData;

            if (!members.contains(uid)) {
                CourtNotice.show(
                        this,
                        "해당 재판의 구성원이 아닙니다."
                );
                finish();
                return post;
            }

            for (Object member : members) {

                if (member instanceof String
                        && !uid.equals(member)) {

                    partnerUid = (String) member;
                    break;
                }
            }
        }

        return post;
    }

    private long timeOf(
            DocumentSnapshot doc,
            String field) {

        Timestamp timestamp = doc.getTimestamp(field);

        return timestamp == null
                ? 0L
                : timestamp.toDate().getTime();
    }

    // ==========================================
    // 배운 점 제출
    // ==========================================

    private void submit() {

        if (saving
                || submittedLocally
                || currentPost == null
                || currentPost.isResolved()) {
            return;
        }

        if (lessonMap.containsKey(uid)) {
            return;
        }

        String lesson =
                editLesson.getText().toString().trim();

        if (lesson.isEmpty()) {

            CourtNotice.show(
                    this,
                    getString(
                            R.string.court_reconcile_lesson_empty
                    )
            );

            return;
        }

        if (lesson.length() > 2000) {

            CourtNotice.show(
                    this,
                    "배운 점은 2000자 이하로 입력해주세요."
            );

            return;
        }

        setSaving(true);

        // CourtRepository가 Firestore에 실제 저장
        CourtRepository.submitMyLesson(postId, lesson)
                .addOnSuccessListener(this, unused -> {

                    setSaving(false);
                    submittedLocally = true;

                    render();
                })

                .addOnFailureListener(this, error -> {

                    setSaving(false);

                    CourtNotice.show(
                            this,
                            "저장 실패: " + error.getMessage()
                    );

                    render();
                });
    }

    private void setSaving(boolean value) {

        saving = value;

        if (btnSubmit != null) {
            btnSubmit.setEnabled(!value);
        }

        if (editLesson != null) {
            editLesson.setEnabled(!value);
        }
    }

    // ==========================================
    // 두 사람 모두 제출했는지 확인
    // ==========================================

    private void tryResolveIfBothWrote() {

        if (resolving
                || postRef == null
                || currentPost == null
                || currentPost.isResolved()
                || partnerUid == null) {
            return;
        }

        // 두 사람의 배운 점이 모두 있어야 함
        if (!lessonMap.containsKey(uid)
                || !lessonMap.containsKey(partnerUid)) {
            return;
        }

        resolving = true;

        // Firestore 트랜잭션으로 최종 확인
        db.runTransaction(transaction -> {

            DocumentSnapshot post =
                    transaction.get(postRef);

            if (!post.exists()
                    || post.getTimestamp("resolvedAt") != null) {
                return null;
            }

            Object memberData = post.get("memberUids");

            if (!(memberData instanceof List)) {
                return null;
            }

            List<?> members = (List<?>) memberData;

            if (members.size() != 2
                    || !members.contains(uid)) {
                return null;
            }

            Object first = members.get(0);
            Object second = members.get(1);

            if (!(first instanceof String)
                    || !(second instanceof String)) {
                return null;
            }

            DocumentReference lessonA =
                    postRef.collection("lessons")
                            .document((String) first);

            DocumentReference lessonB =
                    postRef.collection("lessons")
                            .document((String) second);

            // 모든 읽기 완료 후 쓰기
            DocumentSnapshot docA =
                    transaction.get(lessonA);

            DocumentSnapshot docB =
                    transaction.get(lessonB);

            if (docA.exists() && docB.exists()) {

                transaction.update(
                        postRef,
                        "resolvedAt",
                        FieldValue.serverTimestamp()
                );
            }

            return null;


        }).addOnCompleteListener(this, task -> {

            resolving = false;

            if (!task.isSuccessful()) {

                // 다른 기기에서 이미 화해 완료했을 수도 있으므로
                // 서버의 최종 상태를 확인한 뒤 오류 표시
                postRef.get(
                        com.google.firebase.firestore.Source.SERVER
                ).addOnSuccessListener(this, latest -> {

                    if (latest.exists()
                            && latest.getTimestamp("resolvedAt") == null) {

                        CourtNotice.show(
                                this,
                                "화해 완료 상태를 저장하지 못했습니다."
                        );
                    }
                }).addOnFailureListener(this, error -> {

                    android.util.Log.w(
                            "CourtReconcile",
                            "화해 상태 확인 실패",
                            error
                    );
                });
            }
        });

    }

    // ==========================================
    // 화해 화면 갱신
    // ==========================================

    private void render() {

        if (currentPost == null || textTitle == null) {
            return;
        }

        CourtPost post = currentPost;

        String myLesson = lessonMap.get(uid);

        String partnerLesson =
                partnerUid == null
                        ? null
                        : lessonMap.get(partnerUid);

        post.myLesson = myLesson;
        post.partnerLesson = partnerLesson;

        boolean resolved = post.isResolved();
        boolean iWrote =
                myLesson != null || submittedLocally;

        textTitle.setText("[" + post.title + "]");

        // 화면을 보는 중 화해가 완료되면 표시
        if (resolved && !wasResolved) {

            wasResolved = true;

            CourtNotice.show(
                    this,
                    "삼켜졌던 별 "
                            + post.returnedStars(availableStars)
                            + "개가 돌아왔어요 ✨"
            );
        }

        if (resolved) {

            // 화해 완료 상태
            textSwallowedLabel.setText(
                    R.string.court_reconcile_returned
            );

            textSwallowed.setText(
                    post.returnedStars(availableStars)
                            + "개"
            );

            textNext.setText(
                    R.string.court_reconcile_done_next
            );

            textGuide.setText(
                    R.string.court_reconcile_guide_done
            );

            textDoneMine.setText(
                    myLesson == null
                            ? "불러오는 중..."
                            : myLesson
            );

            textDonePartner.setText(
                    partnerLesson == null
                            ? "불러오는 중..."
                            : partnerLesson
            );

        } else {

            // 화해 전: 블랙홀이 별을 삼키는 중
            textSwallowedLabel.setText(
                    R.string.court_reconcile_swallowed
            );

            int swallowed =
                    post.swallowedStars(availableStars);

            textSwallowed.setText(swallowed + "개");

            if (swallowed >= availableStars) {

                textNext.setText(
                        R.string.court_reconcile_no_more
                );

            } else {

                long left = StarConsumption.millisUntilNext(
                        post.createdAt,
                        post.resolvedAt,
                        System.currentTimeMillis()
                );

                long seconds = left / 1000L;

                textNext.setText(
                        String.format(
                                Locale.US,
                                "다음 별까지 %02d:%02d",
                                seconds / 60,
                                seconds % 60
                        )
                );
            }

            textGuide.setText(
                    iWrote
                            ? R.string.court_reconcile_guide_wait
                            : R.string.court_reconcile_guide_input
            );

            if (iWrote) {

                textWaitingMine.setText(
                        myLesson == null
                                ? "저장된 내용을 불러오는 중..."
                                : myLesson
                );
            }
        }

        layoutInput.setVisibility(
                !resolved && !iWrote
                        ? View.VISIBLE
                        : View.GONE
        );

        layoutWaiting.setVisibility(
                !resolved && iWrote
                        ? View.VISIBLE
                        : View.GONE
        );

        layoutDone.setVisibility(
                resolved
                        ? View.VISIBLE
                        : View.GONE
        );

        btnSubmit.setEnabled(!saving && !iWrote);

        if (btnPartnerTest != null) {
            btnPartnerTest.setVisibility(View.GONE);
        }
    }

    // ==========================================
    // 생명주기
    // ==========================================

    @Override
    protected void onResume() {
        super.onResume();

        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    @Override
    protected void onPause() {

        handler.removeCallbacks(ticker);

        super.onPause();
    }

    @Override
    protected void onStop() {

        stopWatching();

        super.onStop();
    }


    private void loadAvailableStars(String galaxyId) {

        if (galaxyId == null
                || galaxyId.equals(starsGalaxyId)) {
            return;
        }

        starsGalaxyId = galaxyId;

        db.collection("galaxies")
                .document(galaxyId)
                .get()
                .addOnSuccessListener(this, galaxy -> {

                    if (!galaxy.exists()) {
                        return;
                    }

                    String datingStartDate =
                            galaxy.getString("datingStartDate");

                    if (datingStartDate == null) {
                        return;
                    }

                    try {

                        String[] parts =
                                datingStartDate.split("-");

                        int year = Integer.parseInt(parts[0]);
                        int month = Integer.parseInt(parts[1]);
                        int day = Integer.parseInt(parts[2]);

                        TimeZone utc =
                                TimeZone.getTimeZone("UTC");

                        Calendar start =
                                Calendar.getInstance(utc);

                        start.clear();
                        start.setLenient(false);
                        start.set(year, month - 1, day);

                        Calendar localNow =
                                Calendar.getInstance();

                        Calendar today =
                                Calendar.getInstance(utc);

                        today.clear();
                        today.set(
                                localNow.get(Calendar.YEAR),
                                localNow.get(Calendar.MONTH),
                                localNow.get(Calendar.DAY_OF_MONTH)
                        );

                        long days =
                                (today.getTimeInMillis()
                                        - start.getTimeInMillis())
                                        / 86400000L;

                        availableStars = (int) Math.max(
                                0L,
                                Math.min(days, Integer.MAX_VALUE)
                        );

                        render();

                    } catch (Exception e) {

                        CourtNotice.show(
                                this,
                                "연애 시작일을 확인할 수 없습니다."
                        );
                    }
                })
                .addOnFailureListener(this, e -> {

                    CourtNotice.show(
                            this,
                            "은하계 정보 조회 실패: "
                                    + e.getMessage()
                    );
                });
    }

}

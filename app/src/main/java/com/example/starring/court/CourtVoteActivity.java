
package com.example.starring.court;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.starring.R;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.List;

public class CourtVoteActivity extends AppCompatActivity {

    public static final String EXTRA_POST_ID = "post_id";

    private final FirebaseFirestore db =
            FirebaseFirestore.getInstance();

    private final Handler handler =
            new Handler(Looper.getMainLooper());

    private String postId;
    private String uid;

    private CourtPost currentPost;

    private ListenerRegistration postListener;
    private ListenerRegistration voteListener;

    private boolean voteLoaded = false;
    private boolean voting = false;
    private boolean lastExpired = false;

    private int savedVote = CourtPost.VOTE_NONE;

    private TextView textTitle;
    private TextView textSituation;
    private TextView textPositionA;
    private TextView textPositionB;

    // 입장이 길면 접어 두고 "자세히보기"로 펼침
    private static final int COLLAPSED_LINES = 2;            // 입장 글
    private static final int SITUATION_COLLAPSED_LINES = 3;   // 갈등 상황 글
    private TextView toggleSituation;
    private TextView toggleA;
    private TextView toggleB;
    private TextView textRemaining;
    private TextView textPercentA;
    private TextView textPercentB;
    private TextView textMyVote;
    private TextView textVoteToast;

    private View layoutVoteButtons;
    private View layoutResult;
    private View btnVoteA;
    private View btnVoteB;
    private View btnReconcileGo;

    private View cardA;
    private View cardB;

    private ProgressBar barResult;

    private int remainingNormalColor;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {

            if (currentPost != null) {

                CourtPostAdapter.bindRemaining(
                        textRemaining,
                        currentPost,
                        remainingNormalColor
                );

                if (currentPost.isExpired() != lastExpired) {
                    render();
                }
            }

            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_court_vote);

        getWindow().getDecorView().setBackgroundColor(
                getColor(R.color.court_bg)
        );

        postId = getIntent().getStringExtra(EXTRA_POST_ID);

        FirebaseUser user =
                FirebaseAuth.getInstance().getCurrentUser();

        if (postId == null || user == null) {
            finish();
            return;
        }

        uid = user.getUid();

        textTitle = findViewById(R.id.textTitle);
        textSituation = findViewById(R.id.textSituation);
        textPositionA = findViewById(R.id.textPositionA);
        textPositionB = findViewById(R.id.textPositionB);
        textRemaining = findViewById(R.id.textRemaining);

        textPercentA = findViewById(R.id.textPercentA);
        textPercentB = findViewById(R.id.textPercentB);
        textMyVote = findViewById(R.id.textMyVote);
        textVoteToast = findViewById(R.id.textVoteToast);

        layoutVoteButtons = findViewById(R.id.layoutVoteButtons);
        layoutResult = findViewById(R.id.layoutResult);

        barResult = findViewById(R.id.barResult);

        btnVoteA = findViewById(R.id.btnVoteA);
        btnVoteB = findViewById(R.id.btnVoteB);
        btnReconcileGo = findViewById(R.id.btnReconcileGo);

        toggleSituation = findViewById(R.id.toggleSituation);
        toggleA = findViewById(R.id.toggleA);
        toggleB = findViewById(R.id.toggleB);
        cardA = findViewById(R.id.cardA);
        cardB = findViewById(R.id.cardB);

        remainingNormalColor =
                textRemaining.getCurrentTextColor();

        // 재판 데이터를 읽기 전에는 투표 버튼 비활성화
        layoutVoteButtons.setVisibility(View.GONE);
        layoutResult.setVisibility(View.GONE);
        btnReconcileGo.setVisibility(View.GONE);

        findViewById(R.id.btnBack)
                .setOnClickListener(v -> finish());

        btnVoteA.setOnClickListener(
                v -> vote(CourtPost.VOTE_A)
        );

        btnVoteB.setOnClickListener(
                v -> vote(CourtPost.VOTE_B)
        );

        btnReconcileGo.setOnClickListener(v -> {

            Intent intent = new Intent(
                    this,
                    CourtReconcileActivity.class
            );

            intent.putExtra(
                    CourtReconcileActivity.EXTRA_POST_ID,
                    postId
            );

            startActivity(intent);
        });
    }

    // ==========================================
    // Firebase 실시간 감시
    // ==========================================

    @Override
    protected void onStart() {
        super.onStart();

        if (postId == null || uid == null) {
            return;
        }

        startWatching();
    }

    private void startWatching() {

        stopWatching();

        DocumentReference postRef =
                db.collection("courtPosts").document(postId);

        DocumentReference voteRef =
                postRef.collection("votes").document(uid);

        // 1. 재판 내용과 투표 집계 감시
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
                                "삭제되었거나 존재하지 않는 재판입니다."
                        );
                        finish();
                        return;
                    }

                    currentPost = readPost(snapshot);
                    render();
                }
        );

        // 2. 현재 계정의 투표 기록 감시
        voteListener = voteRef.addSnapshotListener(
                (snapshot, error) -> {

                    if (error != null) {
                        voteLoaded = false;

                        CourtNotice.show(
                                this,
                                "투표 기록 확인 실패: "
                                        + error.getMessage()
                        );

                        render();
                        return;
                    }

                    if (snapshot == null) {
                        return;
                    }

                    savedVote = CourtPost.VOTE_NONE;

                    if (snapshot.exists()) {

                        Long choice = snapshot.getLong("choice");

                        if (choice != null) {
                            savedVote = choice.intValue();
                        }
                    }

                    voteLoaded = true;
                    render();
                }
        );
    }

    private void stopWatching() {

        if (postListener != null) {
            postListener.remove();
            postListener = null;
        }

        if (voteListener != null) {
            voteListener.remove();
            voteListener = null;
        }
    }

    // ==========================================
    // Firestore 문서 -> CourtPost
    // ==========================================

    private CourtPost readPost(DocumentSnapshot doc) {

        CourtPost post = new CourtPost();

        post.id = doc.getId();
        post.title = doc.getString("title");
        post.situation = doc.getString("situation");
        post.positionA = doc.getString("positionA");
        post.positionB = doc.getString("positionB");
        post.galaxyName = doc.getString("galaxyName");

        Long a = doc.getLong("votesA");
        Long b = doc.getLong("votesB");

        post.votesA = a == null ? 0 : a.intValue();
        post.votesB = b == null ? 0 : b.intValue();

        post.createdAt = timeOf(doc, "createdAt");
        post.deadlineAt = timeOf(doc, "deadlineAt");
        post.resolvedAt = timeOf(doc, "resolvedAt");

        // 재판을 만든 커플의 두 계정은 투표 불가
        Object members = doc.get("memberUids");

        post.mine = members instanceof List
                && ((List<?>) members).contains(uid);

        post.myVote = savedVote;

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
    // 투표
    // ==========================================

    private void vote(int choice) {

        if (currentPost == null
                || !voteLoaded
                || voting) {
            return;
        }

        if (currentPost.mine) {
            CourtNotice.show(
                    this,
                    "내 커플의 재판에는 투표할 수 없습니다."
            );
            return;
        }

        if (currentPost.isExpired()) {
            CourtNotice.show(
                    this,
                    "투표가 마감되었습니다."
            );
            return;
        }

        if (savedVote != CourtPost.VOTE_NONE) {
            CourtNotice.show(
                    this,
                    "이미 투표한 재판입니다."
            );
            return;
        }

        voting = true;
        render();

        CourtRepository.vote(postId, choice)
                .addOnSuccessListener(this, unused -> {

                    voting = false;

                    // 서버 저장 성공 후 화면 반영
                    savedVote = choice;
                    currentPost.myVote = choice;

                    render();

                    shakeScreen();
                    showVoteToast();
                })
                .addOnFailureListener(this, error -> {

                    voting = false;

                    CourtNotice.show(
                            this,
                            "투표 실패: " + error.getMessage()
                    );

                    render();
                });
    }

    // ==========================================
    // 화면 갱신
    // ==========================================

    private void render() {

        if (currentPost == null || textTitle == null) {
            return;
        }

        CourtPost post = currentPost;
        post.myVote = savedVote;

        textTitle.setText(post.title);
        bindCollapsible(textSituation, toggleSituation, post.situation, SITUATION_COLLAPSED_LINES);
        bindCollapsible(textPositionA, toggleA, post.positionA, COLLAPSED_LINES);
        bindCollapsible(textPositionB, toggleB, post.positionB, COLLAPSED_LINES);

        CourtPostAdapter.bindRemaining(
                textRemaining,
                post,
                remainingNormalColor
        );

        boolean voted =
                savedVote != CourtPost.VOTE_NONE;

        boolean expired = post.isExpired();
        boolean mine = post.mine;

        lastExpired = expired;

        boolean showResult = voted || expired || mine;

        layoutVoteButtons.setVisibility(
                showResult ? View.GONE : View.VISIBLE
        );

        layoutResult.setVisibility(
                showResult ? View.VISIBLE : View.GONE
        );

        boolean canVote =
                voteLoaded
                        && !voting
                        && !showResult;

        btnVoteA.setEnabled(canVote);
        btnVoteB.setEnabled(canVote);

        setDim(cardA, voted || expired);
        setDim(cardB, voted || expired);

        btnReconcileGo.setVisibility(
                mine && !post.isResolved()
                        ? View.VISIBLE
                        : View.GONE
        );

        if (showResult) {

            barResult.setProgress(post.percentA());

            textPercentA.setText(
                    "A " + post.percentA() + "%"
            );

            textPercentB.setText(
                    "B " + post.percentB() + "%"
            );

            if (voted) {

                textMyVote.setText(
                        savedVote == CourtPost.VOTE_A
                                ? R.string.court_voted_a
                                : R.string.court_voted_b
                );

            } else if (expired) {

                textMyVote.setText("투표가 마감되었어요");

            } else {

                textMyVote.setText(
                        "내가 올린 블랙홀이에요. "
                                + "투표 결과를 확인해 보세요"
                );
            }
        }
    }

    // ==========================================
    // 화면 생명주기
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

    // ==========================================
    // 기존 흔들림 애니메이션 유지
    // ==========================================

    /**
     * 입장 글이 COLLAPSED_LINES 줄을 넘으면 그 줄까지만 보여 주고
     * "자세히보기"를 눌러 펼치게 함. 화면 갱신 때마다 불리므로
     * 내용이 같으면 펼침/접힘 상태를 그대로 둠.
     */
    private void bindCollapsible(
            TextView body,
            TextView toggle,
            String content,
            final int limit) {

        if (content != null && content.equals(body.getTag())) {
            return;
        }
        body.setTag(content);

        body.setText(content);
        body.setMaxLines(Integer.MAX_VALUE);
        body.setEllipsize(null);
        toggle.setVisibility(View.GONE);

        // 글이 화면에 배치된 뒤에야 줄 수를 알 수 있음
        body.post(() -> {

            if (body.getLineCount() <= limit) {
                return;
            }

            body.setMaxLines(limit);
            body.setEllipsize(TextUtils.TruncateAt.END);
            toggle.setText(R.string.court_see_more);
            toggle.setVisibility(View.VISIBLE);

            toggle.setOnClickListener(v -> {

                boolean collapsed =
                        body.getMaxLines() == limit;

                body.setMaxLines(
                        collapsed ? Integer.MAX_VALUE : limit
                );
                body.setEllipsize(
                        collapsed ? null : TextUtils.TruncateAt.END
                );
                toggle.setText(
                        collapsed
                                ? R.string.court_see_less
                                : R.string.court_see_more
                );
            });
        });
    }

    private void shakeScreen() {

        View content =
                findViewById(android.R.id.content);

        if (content == null) {
            return;
        }

        float d =
                getResources().getDisplayMetrics().density;

        ObjectAnimator animator =
                ObjectAnimator.ofPropertyValuesHolder(
                        content,
                        PropertyValuesHolder.ofFloat(
                                View.TRANSLATION_X,
                                0f,
                                -6f * d,
                                6f * d,
                                -5f * d,
                                5f * d,
                                -3f * d,
                                3f * d,
                                0f
                        ),
                        PropertyValuesHolder.ofFloat(
                                View.TRANSLATION_Y,
                                0f,
                                4f * d,
                                -4f * d,
                                3f * d,
                                -3f * d,
                                2f * d,
                                -2f * d,
                                0f
                        )
                );

        animator.setDuration(450);
        animator.start();

        content.performHapticFeedback(
                android.view.HapticFeedbackConstants.LONG_PRESS
        );
    }

    private void setDim(View card, boolean dim) {

        if (card == null) {
            return;
        }

        if (dim) {

            GradientDrawable shade =
                    new GradientDrawable();

            shade.setColor(0x99000000);

            shade.setCornerRadius(
                    16f * getResources()
                            .getDisplayMetrics().density
            );

            card.setForeground(shade);

        } else {
            card.setForeground(null);
        }
    }

    private void showVoteToast() {

        textVoteToast.animate().cancel();
        textVoteToast.animate().setStartDelay(0);

        textVoteToast.setVisibility(View.VISIBLE);
        textVoteToast.setAlpha(0f);

        textVoteToast.animate()
                .alpha(1f)
                .setDuration(250)
                .withEndAction(() -> {

                    textVoteToast.animate()
                            .alpha(0f)
                            .setStartDelay(1600)
                            .setDuration(400)
                            .withEndAction(() -> {

                                textVoteToast.setVisibility(View.GONE);
                                textVoteToast.animate().setStartDelay(0);
                            });
                });
    }
}

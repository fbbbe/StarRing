package com.example.starring.court;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.starring.R;

import android.graphics.drawable.GradientDrawable;

/**
 * 우주 재판장 투표 화면
 * - 갈등 상황과 양쪽 입장을 보고 A / B 중 한쪽에 익명 투표
 * - 투표 후에는 결과(비율)를 보여줌
 */
public class CourtVoteActivity extends AppCompatActivity {

    public static final String EXTRA_POST_ID = "post_id";

    private String postId;

    private TextView textTitle;
    private TextView textSituation;
    private TextView textPositionA;
    private TextView textPositionB;
    private TextView textRemaining;
    private View layoutVoteButtons;
    private View layoutResult;
    private ProgressBar barResult;
    private TextView textPercentA;
    private TextView textPercentB;
    private TextView textMyVote;

    private View angerA;
    private View angerB;
    private View cardA;
    private View cardB;
    private View btnReconcileGo;
    private TextView textVoteToast;
    private int remainingNormalColor;
    private boolean lastExpired;
    private final Handler handler = new Handler(Looper.getMainLooper());

    // 1초마다 남은 시간 갱신 (목록 화면과 같은 표시 함수 사용)
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            CourtPost post = CourtRepository.findById(postId);
            if (post != null) {
                CourtPostAdapter.bindRemaining(textRemaining, post, remainingNormalColor);
                // 보고 있는 도중 마감되면 화면 전환
                if (post.isExpired() != lastExpired) {
                    render();
                }
            }
            handler.postDelayed(this, 1000);
        }
    };
    private final Runnable wiggleLoop = new Runnable() {
        @Override
        public void run() {
            wiggle(angerA);
            wiggle(angerB);
            handler.postDelayed(this, 3000); // 3초마다 한 번
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_court_vote);
        getWindow().getDecorView().setBackgroundColor(getColor(R.color.court_bg));

        postId = getIntent().getStringExtra(EXTRA_POST_ID);
        if (postId == null || CourtRepository.findById(postId) == null) {
            finish();
            return;
        }

        textTitle = findViewById(R.id.textTitle);
        textSituation = findViewById(R.id.textSituation);
        textPositionA = findViewById(R.id.textPositionA);
        textPositionB = findViewById(R.id.textPositionB);
        textRemaining = findViewById(R.id.textRemaining);
        remainingNormalColor = textRemaining.getCurrentTextColor();
        layoutVoteButtons = findViewById(R.id.layoutVoteButtons);
        layoutResult = findViewById(R.id.layoutResult);
        barResult = findViewById(R.id.barResult);
        textPercentA = findViewById(R.id.textPercentA);
        textPercentB = findViewById(R.id.textPercentB);
        textMyVote = findViewById(R.id.textMyVote);
        angerA = findViewById(R.id.angerA);
        angerB = findViewById(R.id.angerB);
        cardA = findViewById(R.id.cardA);
        cardB = findViewById(R.id.cardB);
        btnReconcileGo = findViewById(R.id.btnReconcileGo);
        btnReconcileGo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(CourtVoteActivity.this, CourtReconcileActivity.class);
                intent.putExtra(CourtReconcileActivity.EXTRA_POST_ID, postId);
                startActivity(intent);
            }
        });
        textVoteToast = findViewById(R.id.textVoteToast);

        findViewById(R.id.btnBack).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        findViewById(R.id.btnVoteA).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                vote(CourtPost.VOTE_A);
            }
        });

        findViewById(R.id.btnVoteB).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                vote(CourtPost.VOTE_B);
            }
        });

        render();
    }

    private void vote(int choice) {
        CourtPost post = CourtRepository.findById(postId);
        if (post == null || post.isExpired() || post.mine || post.myVote != CourtPost.VOTE_NONE) {
            render(); // 마감됐거나 이미 투표함, 내가 올린 글 -> 투표 불가
            return;
        }
        CourtRepository.vote(postId, choice);
        render();
        shakeScreen();   // 투표 후 흔들림
        showVoteToast();
    }

    private void render() {
        CourtPost post = CourtRepository.findById(postId);
        if (post == null) {
            finish();
            return;
        }

        textTitle.setText("[" + post.title + "]");
        textSituation.setText(post.situation);
        textPositionA.setText(post.positionA);
        textPositionB.setText(post.positionB);
        CourtPostAdapter.bindRemaining(textRemaining, post, remainingNormalColor);

        boolean voted = post.myVote != CourtPost.VOTE_NONE;
        boolean expired = post.isExpired();
        boolean mine = post.mine;
        lastExpired = expired;

        // 내가 올린 블랙홀은 투표할 수 없음 -> 결과만 보여주고 [화해하러 가기]
        boolean showResult = voted || expired || mine;
        layoutVoteButtons.setVisibility(showResult ? View.GONE : View.VISIBLE);
        layoutResult.setVisibility(showResult ? View.VISIBLE : View.GONE);

        // 어둡게 처리는 투표했거나 마감된 경우만 (내 글은 입장 카드를 그대로 보여줌)
        setDim(cardA, voted || expired);
        setDim(cardB, voted || expired);

        btnReconcileGo.setVisibility(mine && !post.isResolved() ? View.VISIBLE : View.GONE);

        if (showResult) {
            barResult.setProgress(post.percentA());
            textPercentA.setText("A " + post.percentA() + "%");
            textPercentB.setText("B " + post.percentB() + "%");
            if (voted) {
                textMyVote.setText(post.myVote == CourtPost.VOTE_A
                        ? R.string.court_voted_a : R.string.court_voted_b);
            } else if (expired) {
                textMyVote.setText("투표가 마감되었어요");
            } else {
                textMyVote.setText("내가 올린 블랙홀이에요. 투표 결과를 확인해 보세요");
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        render(); // 화해하고 돌아왔을 때 상태 갱신
        handler.postDelayed(wiggleLoop, 800);
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(wiggleLoop);
        handler.removeCallbacks(ticker);
        super.onPause();
    }

    // 💢가 살짝 흔들리며 커졌다 작아짐
    private void wiggle(View v) {
        if (v == null) {
            return;
        }
        ObjectAnimator animator = ObjectAnimator.ofPropertyValuesHolder(v,
                PropertyValuesHolder.ofFloat(View.ROTATION, 0f, -14f, 14f, -10f, 10f, 0f),
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.2f, 1f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.2f, 1f));
        animator.setDuration(600);
        animator.start();
    }

    // 투표하면 화면 전체가 미세하게 떨림 (우주의 균형이 흔들리는 느낌)
    private void shakeScreen() {
        View content = findViewById(android.R.id.content);
        if (content == null) {
            return;
        }
        float d = getResources().getDisplayMetrics().density;
        ObjectAnimator animator = ObjectAnimator.ofPropertyValuesHolder(content,
                PropertyValuesHolder.ofFloat(View.TRANSLATION_X,
                        0f, -6f * d, 6f * d, -5f * d, 5f * d, -3f * d, 3f * d, 0f),
                PropertyValuesHolder.ofFloat(View.TRANSLATION_Y,
                        0f, 4f * d, -4f * d, 3f * d, -3f * d, 2f * d, -2f * d, 0f));
        animator.setDuration(450);
        animator.start();
        content.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
    }

    // 투표 후(또는 마감 후) A/B 카드 위에 반투명 검정을 씌움
    private void setDim(View card, boolean dim) {
        if (card == null) {
            return;
        }
        if (dim) {
            GradientDrawable shade = new GradientDrawable();
            shade.setColor(0x99000000); // 앞 두 자리가 불투명도 (99 = 약 60%)
            shade.setCornerRadius(16f * getResources().getDisplayMetrics().density);
            card.setForeground(shade);
        } else {
            card.setForeground(null);
        }
    }

    // "당신의 한 표가..." 메시지: 나타났다가 잠시 후 사라짐
    private void showVoteToast() {
        textVoteToast.animate().cancel();
        textVoteToast.setVisibility(View.VISIBLE);
        textVoteToast.setAlpha(0f);
        textVoteToast.animate()
                .alpha(1f)
                .setDuration(250)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        textVoteToast.animate()
                                .alpha(0f)
                                .setStartDelay(1600) // 1.6초 머문 뒤
                                .setDuration(400)    // 0.4초 동안 사라짐
                                .withEndAction(new Runnable() {
                                    @Override
                                    public void run() {
                                        textVoteToast.setVisibility(View.GONE);
                                        textVoteToast.animate().setStartDelay(0);
                                    }
                                });
                    }
                });
    }
}

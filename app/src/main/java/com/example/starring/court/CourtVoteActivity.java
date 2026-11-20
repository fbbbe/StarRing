package com.example.starring.court;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.starring.R;

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
        if (post == null || post.isExpired() || post.myVote != CourtPost.VOTE_NONE) {
            render(); // 마감됐거나 이미 투표함 -> 투표 불가
            return;
        }
        CourtRepository.vote(postId, choice);
        render();
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
        lastExpired = expired;

        // 투표 전(마감 전): 버튼만 / 투표했거나 마감됨: 결과만
        boolean showResult = voted || expired;
        layoutVoteButtons.setVisibility(showResult ? View.GONE : View.VISIBLE);
        layoutResult.setVisibility(showResult ? View.VISIBLE : View.GONE);

        if (showResult) {
            barResult.setProgress(post.percentA());
            textPercentA.setText("A " + post.percentA() + "%");
            textPercentB.setText("B " + post.percentB() + "%");
            if (voted) {
                textMyVote.setText(post.myVote == CourtPost.VOTE_A
                        ? R.string.court_voted_a : R.string.court_voted_b);
            } else {
                textMyVote.setText("투표가 마감되었어요");
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
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
}

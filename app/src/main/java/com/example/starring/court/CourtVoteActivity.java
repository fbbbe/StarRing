package com.example.starring.court;

import android.os.Bundle;
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
        layoutVoteButtons = findViewById(R.id.layoutVoteButtons);
        layoutResult = findViewById(R.id.layoutResult);
        barResult = findViewById(R.id.barResult);
        textPercentA = findViewById(R.id.textPercentA);
        textPercentB = findViewById(R.id.textPercentB);
        textMyVote = findViewById(R.id.textMyVote);

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
        textRemaining.setText(post.remainingText());

        boolean voted = post.myVote != CourtPost.VOTE_NONE;

        // 투표 전: 버튼만 / 투표 후: 결과만
        layoutVoteButtons.setVisibility(voted ? View.GONE : View.VISIBLE);
        layoutResult.setVisibility(voted ? View.VISIBLE : View.GONE);

        if (voted) {
            barResult.setProgress(post.percentA());
            textPercentA.setText("A " + post.percentA() + "%");
            textPercentB.setText("B " + post.percentB() + "%");
            textMyVote.setText(post.myVote == CourtPost.VOTE_A
                    ? R.string.court_voted_a : R.string.court_voted_b);
        }
    }
}

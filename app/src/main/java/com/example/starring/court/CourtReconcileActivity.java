package com.example.starring.court;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.starring.R;

import java.util.Locale;

/**
 * 화해 화면 (블랙홀을 만든 사람이 화해하는 화면)
 * - 지금까지 블랙홀이 삼킨 별 개수를 실시간으로 보여줌
 * - [화해하기] -> 해결 시각 기록 -> 삼킨 별이 모두 돌아옴
 */
public class CourtReconcileActivity extends AppCompatActivity {

    public static final String EXTRA_POST_ID = "post_id";

    // TODO(은하계 연동 후): 임시값 -> 우리 은하계의 실제 애기별 개수(경과 일수)로 교체
    private static final int AVAILABLE_STARS = 20;

    private String postId;
    private boolean justResolved;

    private TextView textTitle;
    private TextView textSwallowed;
    private TextView textNext;
    private TextView textGuide;
    private TextView btnReconcile;
    private TextView textReturnToast;

    private final Handler handler = new Handler(Looper.getMainLooper());
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
        if (postId == null || CourtRepository.findById(postId) == null) {
            finish();
            return;
        }

        textTitle = findViewById(R.id.textTitle);
        textSwallowed = findViewById(R.id.textSwallowed);
        textNext = findViewById(R.id.textNext);
        textGuide = findViewById(R.id.textGuide);
        btnReconcile = findViewById(R.id.btnReconcile);
        textReturnToast = findViewById(R.id.textReturnToast);

        findViewById(R.id.btnBack).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        btnReconcile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                reconcile();
            }
        });

        render();
    }

    private void reconcile() {
        CourtPost post = CourtRepository.findById(postId);
        if (post == null || post.isResolved()) {
            return;
        }
        int returned = post.swallowedStars(AVAILABLE_STARS); // 돌려받을 별 (화해 직전 값)
        CourtRepository.resolve(postId);
        justResolved = true;
        render();
        showReturnToast("삼켜졌던 별 " + returned + "개가 돌아왔어요 ✨");
    }

    private void render() {
        CourtPost post = CourtRepository.findById(postId);
        if (post == null) {
            finish();
            return;
        }

        textTitle.setText("[" + post.title + "]");

        if (post.isResolved()) {
            // 화해 완료 상태
            textSwallowed.setText("0개");
            textNext.setText(R.string.court_reconcile_done_next);
            textGuide.setText(justResolved
                    ? R.string.court_reconcile_done_guide
                    : R.string.court_reconcile_already);
            btnReconcile.setVisibility(View.GONE);
            return;
        }

        int swallowed = post.swallowedStars(AVAILABLE_STARS);
        textSwallowed.setText(swallowed + "개");

        if (swallowed >= AVAILABLE_STARS) {
            textNext.setText(R.string.court_reconcile_no_more);
        } else {
            long left = StarConsumption.millisUntilNext(
                    post.createdAt, post.resolvedAt, System.currentTimeMillis());
            long sec = left / 1000L;
            textNext.setText(String.format(Locale.US,
                    "다음 별까지 %02d:%02d", sec / 60, sec % 60));
        }
    }

    // 화해 직후 잠깐 떴다 사라지는 메시지
    private void showReturnToast(String message) {
        textReturnToast.animate().cancel();
        textReturnToast.setText(message);
        textReturnToast.setVisibility(View.VISIBLE);
        textReturnToast.setAlpha(0f);
        textReturnToast.animate()
                .alpha(1f)
                .setDuration(250)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        textReturnToast.animate()
                                .alpha(0f)
                                .setStartDelay(1600)
                                .setDuration(400)
                                .withEndAction(new Runnable() {
                                    @Override
                                    public void run() {
                                        textReturnToast.setVisibility(View.GONE);
                                        textReturnToast.animate().setStartDelay(0);
                                    }
                                });
                    }
                });
    }

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
}

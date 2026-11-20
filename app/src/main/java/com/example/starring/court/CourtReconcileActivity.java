package com.example.starring.court;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.starring.R;

import java.util.Locale;

/**
 * 화해 화면
 * 1) 내 배운점 입력  ->  2) 상대가 쓸 때까지 대기  ->  3) 둘 다 쓰면 화해 완료(삼킨 별 반환)
 * 대기 중에도 블랙홀은 계속 별을 삼킴 (화해 완료 시각에 멈추고 그때까지 삼킨 별이 돌아옴).
 *
 * TODO(DB 연동 후): 상대가 쓴 상태를 Firestore 실시간 리스너로 받기, 테스트 버튼 삭제
 */
public class CourtReconcileActivity extends AppCompatActivity {

    public static final String EXTRA_POST_ID = "post_id";

    // TODO(은하계 연동 후): 임시값 -> 우리 은하계의 실제 애기별 개수(경과 일수)로 교체
    private static final int AVAILABLE_STARS = 20;

    private String postId;
    private boolean wasResolved; // 이 화면을 보는 중에 화해가 완료되는 순간을 감지

    private TextView textTitle;
    private TextView textSwallowedLabel;
    private TextView textSwallowed;
    private TextView textNext;
    private TextView textGuide;
    private View layoutInput;
    private View layoutWaiting;
    private View layoutDone;
    private EditText editLesson;
    private TextView textWaitingMine;
    private TextView textDoneMine;
    private TextView textDonePartner;
    private View btnPartnerTest;

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
        CourtPost first = postId == null ? null : CourtRepository.findById(postId);
        if (first == null) {
            finish();
            return;
        }
        wasResolved = first.isResolved();

        textTitle = findViewById(R.id.textTitle);
        textSwallowedLabel = findViewById(R.id.textSwallowedLabel);
        textSwallowed = findViewById(R.id.textSwallowed);
        textNext = findViewById(R.id.textNext);
        textGuide = findViewById(R.id.textGuide);
        layoutInput = findViewById(R.id.layoutInput);
        layoutWaiting = findViewById(R.id.layoutWaiting);
        layoutDone = findViewById(R.id.layoutDone);
        editLesson = findViewById(R.id.editLesson);
        textWaitingMine = findViewById(R.id.textWaitingMine);
        textDoneMine = findViewById(R.id.textDoneMine);
        textDonePartner = findViewById(R.id.textDonePartner);
        btnPartnerTest = findViewById(R.id.btnPartnerTest);

        findViewById(R.id.btnBack).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        findViewById(R.id.btnSubmit).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                submit();
            }
        });

        // [테스트용] 상대 폰에서 쓴 것처럼 처리
        btnPartnerTest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                CourtRepository.submitPartnerLessonForTest(postId);
                render();
            }
        });

        render();
    }

    private void submit() {
        String lesson = editLesson.getText().toString().trim();
        if (lesson.isEmpty()) {
            CourtNotice.show(this, getString(R.string.court_reconcile_lesson_empty));
            return;
        }
        CourtRepository.submitMyLesson(postId, lesson);
        render();
    }

    private void render() {
        CourtPost post = CourtRepository.findById(postId);
        if (post == null) {
            finish();
            return;
        }

        textTitle.setText("[" + post.title + "]");

        boolean resolved = post.isResolved();
        boolean iWrote = post.myLesson != null;

        // 화면을 보는 중에 화해가 완료되면 안내 메시지
        if (resolved && !wasResolved) {
            wasResolved = true;
            CourtNotice.show(this, "삼켜졌던 별 "
                    + post.returnedStars(AVAILABLE_STARS) + "개가 돌아왔어요 ✨");
        }

        if (resolved) {
            // 3) 화해 완료
            textSwallowedLabel.setText(R.string.court_reconcile_returned);
            textSwallowed.setText(post.returnedStars(AVAILABLE_STARS) + "개");
            textNext.setText(R.string.court_reconcile_done_next);
            textGuide.setText(R.string.court_reconcile_guide_done);
            textDoneMine.setText(post.myLesson);
            textDonePartner.setText(post.partnerLesson);
        } else {
            // 1), 2) 아직 화해 전: 블랙홀은 계속 별을 삼키는 중
            textSwallowedLabel.setText(R.string.court_reconcile_swallowed);
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
            textGuide.setText(iWrote
                    ? R.string.court_reconcile_guide_wait
                    : R.string.court_reconcile_guide_input);
            if (iWrote) {
                textWaitingMine.setText(post.myLesson);
            }
        }

        layoutInput.setVisibility(!resolved && !iWrote ? View.VISIBLE : View.GONE);
        layoutWaiting.setVisibility(!resolved && iWrote ? View.VISIBLE : View.GONE);
        layoutDone.setVisibility(resolved ? View.VISIBLE : View.GONE);
        btnPartnerTest.setVisibility(resolved ? View.GONE : View.VISIBLE);
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

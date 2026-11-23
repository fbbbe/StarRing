
package com.example.starring.court;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.starring.R;

import java.util.List;

public class CourtMainActivity extends AppCompatActivity {

    private CourtPostAdapter adapter;

    private TextView tabLatest;
    private TextView tabDeadline;
    private TextView textEmpty;
    private TextView textNotice;

    private boolean latestFirst = true;

    private final Handler handler =
            new Handler(Looper.getMainLooper());

    // Firestore 데이터가 변경되면 목록 다시 표시
    private final Runnable repositoryObserver =
            new Runnable() {
                @Override
                public void run() {
                    refresh();
                }
            };

    // 1초마다 투표 마감 시간 갱신
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {

            if (adapter != null) {
                adapter.tick();
            }

            handler.postDelayed(this, 1000);
        }
    };

    // 재판 작성 화면에서 돌아왔을 때 처리
    private final ActivityResultLauncher<Intent> writeLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    (ActivityResult result) -> {

                        if (result.getResultCode() == RESULT_OK) {

                            showNotice(
                                    getString(R.string.court_created)
                            );

                            refresh();
                        }
                    }
            );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_court_main);

        tabLatest = findViewById(R.id.tabLatest);
        tabDeadline = findViewById(R.id.tabDeadline);
        textEmpty = findViewById(R.id.textEmpty);
        textNotice = findViewById(R.id.textNotice);

        findViewById(R.id.btnBack)
                .setOnClickListener(v -> finish());

        findViewById(R.id.btnMyBlackholes)
                .setOnClickListener(v -> {

                    Intent intent = new Intent(
                            CourtMainActivity.this,
                            CourtMyBlackholesActivity.class
                    );

                    startActivity(intent);
                });

        tabLatest.setOnClickListener(v -> {
            selectTab(true);
        });

        tabDeadline.setOnClickListener(v -> {
            selectTab(false);
        });

        // 재판 카드 클릭 → 투표 화면 이동
        adapter = new CourtPostAdapter(
                new CourtPostAdapter.OnPostClickListener() {

                    @Override
                    public void onPostClick(CourtPost post) {

                        Intent intent = new Intent(
                                CourtMainActivity.this,
                                CourtVoteActivity.class
                        );

                        intent.putExtra(
                                CourtVoteActivity.EXTRA_POST_ID,
                                post.id
                        );

                        startActivity(intent);
                    }
                }
        );

        RecyclerView list =
                findViewById(R.id.listPosts);

        list.setLayoutManager(
                new LinearLayoutManager(this)
        );

        list.setAdapter(adapter);

        // 새로운 블랙홀 생성
        findViewById(R.id.btnCreate)
                .setOnClickListener(v -> {

                    if (CourtRepository.hasOpenBlackhole()) {

                        showNotice(
                                getString(
                                        R.string.court_already_open
                                )
                        );

                        return;
                    }

                    Intent intent = new Intent(
                            CourtMainActivity.this,
                            CourtWriteActivity.class
                    );

                    writeLauncher.launch(intent);
                });

        selectTab(true);
    }

    private void selectTab(boolean latest) {

        latestFirst = latest;

        tabLatest.setBackgroundResource(
                latest
                        ? R.drawable.court_chip_selected
                        : R.drawable.court_chip_unselected
        );

        tabDeadline.setBackgroundResource(
                latest
                        ? R.drawable.court_chip_unselected
                        : R.drawable.court_chip_selected
        );

        tabLatest.setTextColor(
                getColor(
                        latest
                                ? R.color.court_text
                                : R.color.court_text_sub
                )
        );

        tabDeadline.setTextColor(
                getColor(
                        latest
                                ? R.color.court_text_sub
                                : R.color.court_text
                )
        );

        refresh();
    }

    // Firestore에서 갱신된 캐시를 화면에 표시
    private void refresh() {

        if (adapter == null || textEmpty == null) {
            return;
        }

        List<CourtPost> posts =
                CourtRepository.getPosts(latestFirst);

        adapter.submit(posts);

        textEmpty.setVisibility(
                posts.isEmpty() ? View.VISIBLE : View.GONE
        );
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Firebase 데이터 변경 감지 시작
        CourtRepository.addObserver(repositoryObserver);

        refresh();

        handler.removeCallbacks(ticker);
        handler.postDelayed(ticker, 1000);
    }

    @Override
    protected void onPause() {

        // 화면이 보이지 않으면 UI 콜백 해제
        CourtRepository.removeObserver(repositoryObserver);

        handler.removeCallbacks(ticker);

        super.onPause();
    }

    private void showNotice(String message) {

        if (textNotice == null) {
            return;
        }

        textNotice.animate().cancel();
        textNotice.animate().setStartDelay(0);

        textNotice.setText(message);
        textNotice.setVisibility(View.VISIBLE);
        textNotice.setAlpha(0f);

        textNotice.animate()
                .alpha(1f)
                .setDuration(250)
                .withEndAction(() -> {

                    textNotice.animate()
                            .alpha(0f)
                            .setStartDelay(1600)
                            .setDuration(400)
                            .withEndAction(() -> {

                                textNotice.setVisibility(View.GONE);
                                textNotice.animate().setStartDelay(0);
                            });
                });
    }
}

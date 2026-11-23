
package com.example.starring.court;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.starring.R;

import java.util.List;

public class CourtMyBlackholesActivity extends AppCompatActivity {

    private CourtPostAdapter ongoingAdapter;
    private CourtPostAdapter pastAdapter;

    private View textEmptyOngoing;
    private View textEmptyPast;

    private final Handler handler =
            new Handler(Looper.getMainLooper());

    // Firebase 데이터 변경 감지
    private final Runnable repositoryObserver =
            new Runnable() {
                @Override
                public void run() {
                    refresh();
                }
            };

    // 1초마다 남은 시간 갱신
    private final Runnable ticker = new Runnable() {

        @Override
        public void run() {

            if (ongoingAdapter != null) {
                ongoingAdapter.tick();
            }

            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_court_my);

        getWindow().getDecorView().setBackgroundColor(
                getColor(R.color.court_bg)
        );

        findViewById(R.id.btnBack)
                .setOnClickListener(v -> finish());

        textEmptyOngoing =
                findViewById(R.id.textEmptyOngoing);

        textEmptyPast =
                findViewById(R.id.textEmptyPast);

        // 진행 중인 블랙홀
        ongoingAdapter = new CourtPostAdapter(
                new CourtPostAdapter.OnPostClickListener() {

                    @Override
                    public void onPostClick(CourtPost post) {

                        Intent intent = new Intent(
                                CourtMyBlackholesActivity.this,
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

        // 화해가 완료된 블랙홀
        pastAdapter = new CourtPostAdapter(
                new CourtPostAdapter.OnPostClickListener() {

                    @Override
                    public void onPostClick(CourtPost post) {

                        Intent intent = new Intent(
                                CourtMyBlackholesActivity.this,
                                CourtReconcileActivity.class
                        );

                        intent.putExtra(
                                CourtReconcileActivity.EXTRA_POST_ID,
                                post.id
                        );

                        startActivity(intent);
                    }
                }
        );

        setupList(
                findViewById(R.id.listOngoing),
                ongoingAdapter
        );

        setupList(
                findViewById(R.id.listPast),
                pastAdapter
        );

        refresh();
    }

    private void setupList(
            RecyclerView list,
            CourtPostAdapter adapter) {

        list.setLayoutManager(
                new LinearLayoutManager(this)
        );

        list.setNestedScrollingEnabled(false);

        list.setAdapter(adapter);
    }

    private void refresh() {

        if (ongoingAdapter == null || pastAdapter == null) {
            return;
        }

        // 화해 전인 블랙홀
        List<CourtPost> ongoing =
                CourtRepository.getMyPosts(false);

        // 화해 완료된 블랙홀
        List<CourtPost> past =
                CourtRepository.getMyPosts(true);

        ongoingAdapter.submit(ongoing);
        pastAdapter.submit(past);

        textEmptyOngoing.setVisibility(
                ongoing.isEmpty()
                        ? View.VISIBLE
                        : View.GONE
        );

        textEmptyPast.setVisibility(
                past.isEmpty()
                        ? View.VISIBLE
                        : View.GONE
        );
    }

    @Override
    protected void onResume() {
        super.onResume();

        CourtRepository.addObserver(repositoryObserver);

        refresh();

        handler.removeCallbacks(ticker);
        handler.postDelayed(ticker, 1000);
    }

    @Override
    protected void onPause() {

        CourtRepository.removeObserver(repositoryObserver);

        handler.removeCallbacks(ticker);

        super.onPause();
    }
}

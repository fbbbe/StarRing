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

/**
 * 내 블랙홀 화면 (재판장 메인 우측 상단 [내 블랙홀] 버튼으로 진입)
 * - 진행 중: 화해 전인 내 블랙홀 -> 누르면 투표 현황 + [화해하러 가기]
 * - 지난 블랙홀: 화해 완료된 내 블랙홀(어둡게 표시) -> 누르면 서로의 배운점 보기
 *
 * TODO(DB 연동 후): CourtRepository.getMyPosts() 가 Firestore 에서 내 커플의 글을 조회
 */
public class CourtMyBlackholesActivity extends AppCompatActivity {

    private CourtPostAdapter ongoingAdapter;
    private CourtPostAdapter pastAdapter;
    private View textEmptyOngoing;
    private View textEmptyPast;

    // 진행 중 카드의 남은 시간을 1초마다 갱신
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            ongoingAdapter.tick();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_court_my);
        getWindow().getDecorView().setBackgroundColor(getColor(R.color.court_bg));

        findViewById(R.id.btnBack).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        textEmptyOngoing = findViewById(R.id.textEmptyOngoing);
        textEmptyPast = findViewById(R.id.textEmptyPast);

        // 진행 중 -> 투표 현황 화면 (거기서 [화해하러 가기])
        ongoingAdapter = new CourtPostAdapter(new CourtPostAdapter.OnPostClickListener() {
            @Override
            public void onPostClick(CourtPost post) {
                Intent intent = new Intent(CourtMyBlackholesActivity.this, CourtVoteActivity.class);
                intent.putExtra(CourtVoteActivity.EXTRA_POST_ID, post.id);
                startActivity(intent);
            }
        });

        // 지난 블랙홀 -> 화해 화면 (완료 상태: 서로의 배운점)
        pastAdapter = new CourtPostAdapter(new CourtPostAdapter.OnPostClickListener() {
            @Override
            public void onPostClick(CourtPost post) {
                Intent intent = new Intent(CourtMyBlackholesActivity.this, CourtReconcileActivity.class);
                intent.putExtra(CourtReconcileActivity.EXTRA_POST_ID, post.id);
                startActivity(intent);
            }
        });

        setupList((RecyclerView) findViewById(R.id.listOngoing), ongoingAdapter);
        setupList((RecyclerView) findViewById(R.id.listPast), pastAdapter);
    }

    private void setupList(RecyclerView list, CourtPostAdapter adapter) {
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setNestedScrollingEnabled(false); // 바깥 ScrollView 와 함께 스크롤
        list.setAdapter(adapter);
    }

    private void refresh() {
        List<CourtPost> ongoing = CourtRepository.getMyPosts(false);
        List<CourtPost> past = CourtRepository.getMyPosts(true);
        ongoingAdapter.submit(ongoing);
        pastAdapter.submit(past);
        textEmptyOngoing.setVisibility(ongoing.isEmpty() ? View.VISIBLE : View.GONE);
        textEmptyPast.setVisibility(past.isEmpty() ? View.VISIBLE : View.GONE);
    }

    // 화해하고 돌아왔을 때 진행 중 -> 지난 블랙홀로 옮겨지도록 갱신
    @Override
    protected void onResume() {
        super.onResume();
        refresh();
        handler.removeCallbacks(ticker);
        handler.postDelayed(ticker, 1000);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(ticker);
        super.onPause();
    }
}

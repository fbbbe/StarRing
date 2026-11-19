package com.example.starring.court;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.starring.R;


import java.util.List;

/**
 * 우주 재판장 메인 화면
 * - 갈등 목록 (최신순 / 마감임박순)
 * - 카드 터치 -> 투표 화면 (CourtVoteActivity)
 * - [+ 내 갈등 블랙홀 생성] -> 작성 화면 (CourtWriteActivity)
 */
public class CourtMainActivity extends AppCompatActivity {

    private CourtPostAdapter adapter;
    private TextView tabLatest;
    private TextView tabDeadline;
    private TextView textEmpty;
    private boolean latestFirst = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_court_main);

        findViewById(R.id.btnBack).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        tabLatest = findViewById(R.id.tabLatest);
        tabDeadline = findViewById(R.id.tabDeadline);
        textEmpty = findViewById(R.id.textEmpty);

        tabLatest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectTab(true);
            }
        });
        tabDeadline.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectTab(false);
            }
        });

        adapter = new CourtPostAdapter(new CourtPostAdapter.OnPostClickListener() {
            @Override
            public void onPostClick(CourtPost post) {
                Intent intent = new Intent(CourtMainActivity.this, CourtVoteActivity.class);
                intent.putExtra(CourtVoteActivity.EXTRA_POST_ID, post.id);
                startActivity(intent);
            }
        });

        RecyclerView list = findViewById(R.id.listPosts);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        findViewById(R.id.btnCreate).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(CourtMainActivity.this, CourtWriteActivity.class));
            }
        });

        selectTab(true);
    }

    // 작성/투표 화면에서 돌아왔을 때 목록 갱신
    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void selectTab(boolean latest) {
        latestFirst = latest;

        tabLatest.setBackgroundResource(latest
                ? R.drawable.court_chip_selected : R.drawable.court_chip_unselected);
        tabDeadline.setBackgroundResource(latest
                ? R.drawable.court_chip_unselected : R.drawable.court_chip_selected);

        tabLatest.setTextColor(getColor(latest ? R.color.court_text : R.color.court_text_sub));
        tabDeadline.setTextColor(getColor(latest ? R.color.court_text_sub : R.color.court_text));

        refresh();
    }

    private void refresh() {
        List<CourtPost> posts = CourtRepository.getPosts(latestFirst);
        adapter.submit(posts);
        textEmpty.setVisibility(posts.isEmpty() ? View.VISIBLE : View.GONE);
    }
}

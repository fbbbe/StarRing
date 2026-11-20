package com.example.starring.court;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.starring.R;

import java.util.UUID;

/**
 * 우주 재판장 작성 화면 (블랙홀 생성)
 * [다른 은하계로 전송] -> 글을 재판장에 올리고 재판장 메인으로 돌아감
 *
 * TODO(DB 연동 후): CourtRepository.add() 대신 Firestore 저장
 * TODO(은하계 연동 후): 전송 시 우리 은하계에 블랙홀 생성 + 30분마다 별 소멸 시작
 */
public class CourtWriteActivity extends AppCompatActivity {

    // 투표 마감까지의 시간 (임시: 24시간)
    private static final long VOTE_DURATION = 24L * 3600000L;

    private EditText editTitle;
    private EditText editSituation;
    private EditText editMyPosition;
    private EditText editOtherPosition;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_court_write);

        editTitle = findViewById(R.id.editTitle);
        editSituation = findViewById(R.id.editSituation);
        editMyPosition = findViewById(R.id.editMyPosition);
        editOtherPosition = findViewById(R.id.editOtherPosition);

        findViewById(R.id.btnBack).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        findViewById(R.id.btnSend).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                send();
            }
        });
    }

    private void send() {
        String title = editTitle.getText().toString().trim();
        String situation = editSituation.getText().toString().trim();
        String myPosition = editMyPosition.getText().toString().trim();
        String otherPosition = editOtherPosition.getText().toString().trim();

        if (title.isEmpty() || situation.isEmpty()
                || myPosition.isEmpty() || otherPosition.isEmpty()) {
            Toast.makeText(this, R.string.court_fill_all, Toast.LENGTH_SHORT).show();
            return;
        }

        long now = System.currentTimeMillis();

        CourtPost post = new CourtPost(
                UUID.randomUUID().toString(),
                title,
                situation,
                myPosition,
                otherPosition,
                "우리은하계", // TODO: 실제 은하계 이름
                0, 0,
                now,
                now + VOTE_DURATION);

        post.mine = true;

        CourtRepository.add(post);

        setResult(RESULT_OK);
        if (getCallingActivity() == null) {
            // 은하계 메인처럼 결과를 받지 않는 화면에서 열린 경우: 돌아간 화면이 대신 표시
            CourtNotice.setPending(getString(R.string.court_created));
        }
        finish();
    }
}

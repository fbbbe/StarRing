
package com.example.starring.court;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.starring.R;

public class CourtWriteActivity extends AppCompatActivity {

    private static final int MAX_TITLE = 100;
    private static final int MAX_SITUATION = 2000;
    private static final int MAX_POSITION = 1000;

    private EditText editTitle;
    private EditText editSituation;
    private EditText editMyPosition;
    private EditText editOtherPosition;

    private View btnSend;
    private boolean saving = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_court_write);

        editTitle = findViewById(R.id.editTitle);
        editSituation = findViewById(R.id.editSituation);
        editMyPosition = findViewById(R.id.editMyPosition);
        editOtherPosition = findViewById(R.id.editOtherPosition);

        btnSend = findViewById(R.id.btnSend);

        findViewById(R.id.btnBack).setOnClickListener(v -> {
            if (!saving) {
                finish();
            }
        });

        btnSend.setOnClickListener(v -> send());
    }

    private void send() {

        if (saving) {
            return;
        }

        String title = editTitle.getText()
                .toString().trim();

        String situation = editSituation.getText()
                .toString().trim();

        String myPosition = editMyPosition.getText()
                .toString().trim();

        String otherPosition = editOtherPosition.getText()
                .toString().trim();

        // 필수 입력 확인
        if (title.isEmpty()
                || situation.isEmpty()
                || myPosition.isEmpty()
                || otherPosition.isEmpty()) {

            Toast.makeText(
                    this,
                    R.string.court_fill_all,
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        // Firestore 규칙에 맞춘 길이 확인
        if (title.length() > MAX_TITLE) {
            showMessage("제목은 100자 이하로 입력해주세요.");
            return;
        }

        if (situation.length() > MAX_SITUATION) {
            showMessage("갈등 상황은 2000자 이하로 입력해주세요.");
            return;
        }

        if (myPosition.length() > MAX_POSITION
                || otherPosition.length() > MAX_POSITION) {

            showMessage("각 입장은 1000자 이하로 입력해주세요.");
            return;
        }

        // 진행 중인 블랙홀 확인
        // 실제 중복 생성의 최종 차단은 서버에서도 필요
        if (CourtRepository.hasOpenBlackhole()) {

            showMessage(
                    getString(R.string.court_already_open)
            );

            return;
        }

        // 기존 CourtPost 데이터 구조 사용
        CourtPost post = new CourtPost();

        post.title = title;
        post.situation = situation;
        post.positionA = myPosition;
        post.positionB = otherPosition;

        // Firebase에서 실제 작성자와 은하계 정보 확인
        // id, galaxyName, createdAt 등은 Repository가 설정

        setSaving(true);

        CourtRepository.add(post)
                .addOnSuccessListener(this, unused -> {

                    setSaving(false);

                    // Firebase 저장 성공
                    setResult(RESULT_OK);

                    if (getCallingActivity() == null) {
                        CourtNotice.setPending(
                                getString(R.string.court_created)
                        );
                    }

                    finish();
                })
                .addOnFailureListener(this, e -> {

                    setSaving(false);

                    String message = e.getMessage();

                    if (message == null
                            || message.trim().isEmpty()) {

                        message = "재판 저장에 실패했습니다.";
                    }

                    CourtNotice.show(this, message);
                });
    }

    private void setSaving(boolean value) {

        saving = value;

        btnSend.setEnabled(!value);

        editTitle.setEnabled(!value);
        editSituation.setEnabled(!value);
        editMyPosition.setEnabled(!value);
        editOtherPosition.setEnabled(!value);
    }

    private void showMessage(String message) {

        CourtNotice.show(this, message);
    }

    @Override
    public void onBackPressed() {

        if (!saving) {
            super.onBackPressed();
        }
    }
}

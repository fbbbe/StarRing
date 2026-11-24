
package com.example.starring;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.starring.auth.LoginActivity;
import com.example.starring.court.CourtRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.functions.FirebaseFunctions;

import java.util.HashMap;
import java.util.Map;

public class GalaxyDestroyActivity extends AppCompatActivity {

    private static final String CONFIRM_TEXT =
            "은하계 소멸시키기";

    private EditText editConfirm;
    private Button btnDestroy;
    private boolean deleting = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        createScreen();
    }

    private void createScreen() {

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(22), dp(24), dp(24));
        root.setBackgroundColor(0xFF141126);

        TextView back = new TextView(this);
        back.setText("←");
        back.setTextSize(30);
        back.setTextColor(Color.WHITE);
        back.setGravity(Gravity.CENTER_VERTICAL);

        root.addView(back,
                new LinearLayout.LayoutParams(-1, dp(60)));

        back.setOnClickListener(v -> {
            if (!deleting) finish();
        });

        TextView title = new TextView(this);
        title.setText("은하계 소멸");
        title.setTextSize(28);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(65), 0, dp(30));

        root.addView(title);

        TextView description = new TextView(this);
        description.setText(
                "함께 만들어 온 은하계를 영구적으로 소멸시킵니다.\n\n"
                        + "커플 중 한 명이 진행하면 두 사람의 "
                        + "은하계와 계정이 모두 삭제됩니다.\n\n"
                        + "사진과 추억 기록, 재판 기록도 삭제 대상이며 "
                        + "소멸 이후 복구할 수 없습니다."
        );

        description.setTextSize(16);
        description.setTextColor(0xFFD6D0E7);
        description.setGravity(Gravity.CENTER);
        description.setLineSpacing(dp(5), 1f);

        root.addView(description);

        TextView instruction = new TextView(this);
        instruction.setText(
                "소멸하려면 아래 문구를 정확하게 입력하세요.\n\n"
                        + CONFIRM_TEXT
        );
        instruction.setTextSize(16);
        instruction.setTextColor(0xFFFFA7A7);
        instruction.setGravity(Gravity.CENTER);
        instruction.setPadding(0, dp(45), 0, dp(15));

        root.addView(instruction);

        editConfirm = new EditText(this);
        editConfirm.setSingleLine(true);
        editConfirm.setTextSize(16);
        editConfirm.setTextColor(Color.WHITE);
        editConfirm.setHintTextColor(0xFF918AA6);
        editConfirm.setHint("확인 문구 입력");
        editConfirm.setPadding(
                dp(16), dp(12), dp(16), dp(12)
        );

        GradientDrawable inputBackground =
                new GradientDrawable();

        inputBackground.setColor(0xFF27213F);
        inputBackground.setCornerRadius(dp(12));
        inputBackground.setStroke(dp(1), 0xFF625879);
        editConfirm.setBackground(inputBackground);

        root.addView(editConfirm,
                new LinearLayout.LayoutParams(-1, dp(58)));

        btnDestroy = new Button(this);
        btnDestroy.setText("은하계 소멸하기");
        btnDestroy.setTextColor(Color.WHITE);
        btnDestroy.setTextSize(17);
        btnDestroy.setAllCaps(false);

        GradientDrawable buttonBackground =
                new GradientDrawable();

        buttonBackground.setColor(0xFFB93D59);
        buttonBackground.setCornerRadius(dp(14));
        btnDestroy.setBackground(buttonBackground);

        LinearLayout.LayoutParams buttonParams =
                new LinearLayout.LayoutParams(-1, dp(60));
        buttonParams.topMargin = dp(30);

        root.addView(btnDestroy, buttonParams);

        btnDestroy.setOnClickListener(
                v -> confirmDestroy()
        );

        editConfirm.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(
                    CharSequence s, int start,
                    int count, int after) {
            }

            @Override
            public void onTextChanged(
                    CharSequence s, int start,
                    int before, int count) {
                refreshButton();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        setContentView(root);
        refreshButton();
    }

    private void refreshButton() {

        boolean valid = CONFIRM_TEXT.equals(
                editConfirm.getText().toString()
        );

        btnDestroy.setEnabled(valid && !deleting);
        btnDestroy.setAlpha(
                valid && !deleting ? 1f : 0.4f
        );
    }

    private void confirmDestroy() {

        if (deleting || !CONFIRM_TEXT.equals(
                editConfirm.getText().toString())) {
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("정말 은하계를 소멸시키겠습니까?")
                .setMessage(
                        "커플 두 사람의 계정과 은하계 기록이 "
                                + "삭제됩니다.\n\n"
                                + "상대방의 동의 없이도 진행되며 "
                                + "이 작업은 되돌릴 수 없습니다."
                )
                .setNegativeButton("취소", null)
                .setPositiveButton(
                        "영구 소멸",
                        (dialog, which) -> destroyGalaxy()
                )
                .show();
    }

    private void destroyGalaxy() {

        if (deleting) return;

        if (FirebaseAuth.getInstance()
                .getCurrentUser() == null) {
            showMessage("로그인이 필요합니다.");
            return;
        }

        deleting = true;
        btnDestroy.setText("은하계 소멸 처리 중...");
        refreshButton();

        Map<String, Object> data = new HashMap<>();
        data.put("confirmText", CONFIRM_TEXT);

        FirebaseFunctions
                .getInstance("asia-northeast3")
                .getHttpsCallable("destroyGalaxy")
                .call(data)
                .addOnSuccessListener(this, result -> {

                    CourtRepository.stopListening();
                    FirebaseAuth.getInstance().signOut();

                    Intent intent = new Intent(
                            this,
                            LoginActivity.class
                    );

                    intent.addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK
                                    | Intent.FLAG_ACTIVITY_CLEAR_TASK
                    );

                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(this, error -> {

                    deleting = false;
                    btnDestroy.setText("은하계 소멸하기");
                    refreshButton();

                    showMessage(
                            "소멸 처리 실패: "
                                    + error.getMessage()
                    );
                });
    }

    private void showMessage(String value) {
        Toast.makeText(
                this, value, Toast.LENGTH_LONG
        ).show();
    }

    private int dp(float value) {
        return Math.round(
                value * getResources()
                        .getDisplayMetrics().density
        );
    }

    @Override
    public void onBackPressed() {
        if (!deleting) {
            super.onBackPressed();
        }
    }
}

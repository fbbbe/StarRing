package com.example.starring;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.starring.common.Navigator;
import com.example.starring.data.GalaxyRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class GalaxyDestroyActivity extends Activity {

    private static final String CONFIRM_TEXT =
            "은하계 소멸시키기";

    private EditText editConfirm;
    private Button btnDestroy;

    private boolean busy = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        createScreen();
    }

    private void createScreen() {

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(
                dp(24), dp(20), dp(24), dp(24)
        );
        root.setBackgroundColor(0xFF151225);

        // 뒤로가기
        TextView back = createText(
                "‹", 38, Color.WHITE, true
        );
        back.setGravity(Gravity.CENTER_VERTICAL);
        back.setContentDescription("뒤로가기");

        root.addView(
                back,
                new LinearLayout.LayoutParams(-1, dp(55))
        );

        back.setOnClickListener(v -> {
            if (!busy) finish();
        });

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setGravity(Gravity.CENTER_HORIZONTAL);
        body.setPadding(0, dp(45), 0, dp(20));

        scroll.addView(body);

        root.addView(
                scroll,
                new LinearLayout.LayoutParams(-1, 0, 1f)
        );

        TextView symbol = createText(
                "✦", 70, 0xFF9785D8, false
        );
        symbol.setGravity(Gravity.CENTER);

        body.addView(symbol);

        TextView title = createText(
                "은하계 소멸",
                29,
                Color.WHITE,
                true
        );
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(12), 0, dp(20));

        body.addView(title);

        TextView description = createText(
                "두 사람이 함께 만들어온 은하계의 연결을 종료합니다.\n\n"
                        + "한 명만 소멸을 실행해도 두 사람 모두 "
                        + "기존 은하계에서 연동 해제됩니다.\n\n"
                        + "기존 로그인 계정과 비밀번호는 유지되며 "
                        + "다시 새로운 은하계를 만들 수 있습니다.",
                16,
                0xFFD7D0E5,
                false
        );

        description.setGravity(Gravity.CENTER);
        description.setLineSpacing(dp(5), 1f);

        body.addView(description);

        TextView warning = createText(
                "※ 현재 단계는 연동 해제 기능입니다.\n"
                        + "기존 기록의 영구 삭제는 별도 서버 정리가 필요합니다.",
                13,
                0xFFFFADAD,
                false
        );

        warning.setGravity(Gravity.CENTER);
        warning.setPadding(
                0, dp(30), 0, dp(28)
        );

        body.addView(warning);

        TextView guide = createText(
                "계속하려면 아래 문구를 정확하게 입력하세요.",
                15,
                Color.WHITE,
                true
        );
        guide.setGravity(Gravity.CENTER);

        body.addView(guide);

        TextView confirmLabel = createText(
                CONFIRM_TEXT,
                19,
                0xFFFF9EA5,
                true
        );

        confirmLabel.setGravity(Gravity.CENTER);
        confirmLabel.setPadding(
                0, dp(15), 0, dp(16)
        );

        body.addView(confirmLabel);

        editConfirm = new EditText(this);
        editConfirm.setSingleLine(true);
        editConfirm.setHint("확인 문구 입력");
        editConfirm.setTextSize(16);
        editConfirm.setTextColor(Color.WHITE);
        editConfirm.setHintTextColor(0xFF928BA9);
        editConfirm.setPadding(
                dp(15), dp(12), dp(15), dp(12)
        );

        GradientDrawable inputBackground =
                new GradientDrawable();

        inputBackground.setColor(0xFF28233F);
        inputBackground.setCornerRadius(dp(12));
        inputBackground.setStroke(
                dp(1), 0xFF655A85
        );

        editConfirm.setBackground(inputBackground);

        body.addView(
                editConfirm,
                new LinearLayout.LayoutParams(-1, dp(58))
        );

        // 하단 소멸 버튼
        btnDestroy = new Button(this);
        btnDestroy.setText("은하계 소멸하기");
        btnDestroy.setTextSize(17);
        btnDestroy.setTextColor(Color.WHITE);
        btnDestroy.setAllCaps(false);

        GradientDrawable buttonBackground =
                new GradientDrawable();

        buttonBackground.setColor(0xFFAD425B);
        buttonBackground.setCornerRadius(dp(14));
        btnDestroy.setBackground(buttonBackground);

        LinearLayout.LayoutParams buttonParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(60)
                );

        root.addView(btnDestroy, buttonParams);

        editConfirm.addTextChangedListener(
                new TextWatcher() {

                    @Override
                    public void beforeTextChanged(
                            CharSequence s,
                            int start,
                            int count,
                            int after) {
                    }

                    @Override
                    public void onTextChanged(
                            CharSequence s,
                            int start,
                            int before,
                            int count) {

                        refreshButton();
                    }

                    @Override
                    public void afterTextChanged(
                            Editable s) {
                    }
                }
        );

        btnDestroy.setOnClickListener(
                v -> showFinalConfirmation()
        );

        setContentView(root);
        refreshButton();
    }

    private void refreshButton() {

        boolean valid = CONFIRM_TEXT.equals(
                editConfirm.getText().toString()
        );

        btnDestroy.setEnabled(valid && !busy);
        btnDestroy.setAlpha(
                valid && !busy ? 1f : 0.4f
        );
    }

    private void showFinalConfirmation() {

        if (busy || !CONFIRM_TEXT.equals(
                editConfirm.getText().toString())) {
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("은하계를 소멸시키겠습니까?")
                .setMessage(
                        "상대방의 동의 없이도 두 사람의 "
                                + "커플 연동이 해제됩니다.\n\n"
                                + "기존 로그인 계정은 유지됩니다."
                )
                .setNegativeButton("취소", null)
                .setPositiveButton(
                        "소멸시키기",
                        (dialog, which) -> dissolveGalaxy()
                )
                .show();
    }

    private void dissolveGalaxy() {

        if (busy) return;

        FirebaseUser user = FirebaseAuth
                .getInstance()
                .getCurrentUser();

        if (user == null) {
            Toast.makeText(
                    this,
                    "로그인이 필요합니다.",
                    Toast.LENGTH_SHORT
            ).show();

            Navigator.toLogin(this);
            return;
        }

        busy = true;
        editConfirm.setEnabled(false);
        btnDestroy.setText("소멸 처리 중...");
        refreshButton();

        new GalaxyRepository()
                .dissolveGalaxy(user.getUid())
                .addOnSuccessListener(this, unused -> {

                    // 계정은 삭제하지 않고 로그아웃만 수행
                    FirebaseAuth.getInstance().signOut();

                    // 로그인 화면 이동
                    Navigator.toLogin(this);
                })
                .addOnFailureListener(this, error -> {

                    busy = false;
                    editConfirm.setEnabled(true);
                    btnDestroy.setText("은하계 소멸하기");
                    refreshButton();

                    Toast.makeText(
                            this,
                            "연동 해제 실패: "
                                    + error.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                });
    }

    private TextView createText(
            String value,
            int size,
            int color,
            boolean bold) {

        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);

        if (bold) {
            view.setTypeface(null, Typeface.BOLD);
        }

        return view;
    }

    private int dp(float value) {

        return Math.round(
                value * getResources()
                        .getDisplayMetrics().density
        );
    }

    @Override
    public void onBackPressed() {

        if (!busy) {
            super.onBackPressed();
        }
    }
}

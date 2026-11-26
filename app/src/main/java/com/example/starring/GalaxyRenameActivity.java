
package com.example.starring;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.starring.couple.CoupleInputValidator;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

public class GalaxyRenameActivity extends Activity {

    private final FirebaseFirestore db =
            FirebaseFirestore.getInstance();

    private DocumentReference galaxyRef;
    private ListenerRegistration nameListener;

    private TextView currentNameView;
    private EditText editName;
    private Button btnSave;

    private String currentName;
    private boolean initialized = false;
    private boolean saving = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        createScreen();
        loadGalaxy();
    }

    // ==========================================
    // 화면 생성
    // ==========================================

    private void createScreen() {

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(0xFF151225);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(
                dp(24), dp(24),
                dp(24), dp(24)
        );

        scroll.addView(root);

        // 뒤로가기
        TextView back = createText(
                "‹",
                38,
                Color.WHITE,
                true
        );

        back.setContentDescription("뒤로가기");
        back.setOnClickListener(v -> finish());

        root.addView(back);

        // 제목
        TextView title = createText(
                "은하계 이름 변경",
                28,
                Color.WHITE,
                true
        );

        title.setGravity(Gravity.CENTER);
        title.setPadding(
                0, dp(65),
                0, dp(18)
        );

        root.addView(title);

        TextView description = createText(
                "우리만의 은하계에 새로운 이름을 지어주세요.\n"
                        + "변경한 이름은 두 사람 모두에게 적용됩니다.",
                15,
                0xFFC9C0DF,
                false
        );

        description.setGravity(Gravity.CENTER);
        description.setLineSpacing(dp(5), 1f);

        root.addView(description);

        // 현재 은하계 이름
        TextView currentLabel = createText(
                "현재 은하계 이름",
                15,
                0xFFB8ABD8,
                true
        );

        currentLabel.setPadding(
                0, dp(55),
                0, dp(12)
        );

        root.addView(currentLabel);

        currentNameView = createText(
                "불러오는 중...",
                21,
                0xFFE8D8FF,
                true
        );

        currentNameView.setGravity(Gravity.CENTER);
        currentNameView.setPadding(
                dp(15), dp(22),
                dp(15), dp(22)
        );

        currentNameView.setBackground(
                roundedBackground(
                        0xFF28223F,
                        0xFF5F4F87
                )
        );

        root.addView(
                currentNameView,
                new LinearLayout.LayoutParams(
                        -1, dp(80)
                )
        );

        // 새로운 이름
        TextView newNameLabel = createText(
                "새로운 은하계 이름",
                15,
                0xFFB8ABD8,
                true
        );

        newNameLabel.setPadding(
                0, dp(35),
                0, dp(12)
        );

        root.addView(newNameLabel);

        editName = new EditText(this);
        editName.setSingleLine(true);
        editName.setTextSize(17);
        editName.setTextColor(Color.WHITE);
        editName.setHintTextColor(0xFF8E83A5);
        editName.setHint("새로운 은하계 이름 입력");

        editName.setFilters(
                new InputFilter[]{
                        new InputFilter.LengthFilter(20)
                }
        );

        editName.setPadding(
                dp(16), dp(12),
                dp(16), dp(12)
        );

        editName.setBackground(
                roundedBackground(
                        0xFF28223F,
                        0xFF655A85
                )
        );

        root.addView(
                editName,
                new LinearLayout.LayoutParams(
                        -1, dp(58)
                )
        );

        TextView guide = createText(
                "1~20자 이내로 입력해주세요.",
                13,
                0xFF9689B5,
                false
        );

        guide.setPadding(
                0, dp(9),
                0, dp(28)
        );

        root.addView(guide);

        // 변경 버튼
        btnSave = new Button(this);
        btnSave.setText("은하계 이름 변경하기");
        btnSave.setTextSize(16);
        btnSave.setAllCaps(false);
        btnSave.setTextColor(Color.WHITE);

        btnSave.setBackground(
                roundedBackground(
                        0xFF7860CD,
                        0
                )
        );

        root.addView(
                btnSave,
                new LinearLayout.LayoutParams(
                        -1, dp(60)
                )
        );

        btnSave.setEnabled(false);
        btnSave.setAlpha(0.5f);

        btnSave.setOnClickListener(v -> saveName());

        setContentView(scroll);
    }

    // ==========================================
    // 현재 로그인한 사용자의 은하계 조회
    // ==========================================

    private void loadGalaxy() {

        FirebaseUser user = FirebaseAuth
                .getInstance()
                .getCurrentUser();

        if (user == null) {
            message("로그인이 필요합니다.");
            finish();
            return;
        }

        db.collection("users")
                .document(user.getUid())
                .get()
                .addOnSuccessListener(this, userDoc -> {

                    if (!userDoc.exists()) {
                        message("회원 정보를 찾을 수 없습니다.");
                        finish();
                        return;
                    }

                    String galaxyId =
                            userDoc.getString("galaxyId");

                    if (galaxyId == null
                            || galaxyId.isEmpty()) {

                        message("연결된 은하계가 없습니다.");
                        finish();
                        return;
                    }

                    galaxyRef = db.collection("galaxies")
                            .document(galaxyId);

                    startNameListener();
                })
                .addOnFailureListener(this, e -> {

                    message(
                            "은하계 조회 실패: "
                                    + e.getMessage()
                    );
                });
    }

    // ==========================================
    // 은하계 이름 실시간 조회
    // ==========================================

    private void startNameListener() {

        if (galaxyRef == null
                || nameListener != null) {
            return;
        }

        nameListener = galaxyRef.addSnapshotListener(
                (snapshot, error) -> {

                    if (error != null) {
                        message(
                                "이름 조회 실패: "
                                        + error.getMessage()
                        );
                        return;
                    }

                    if (snapshot == null
                            || !snapshot.exists()) {
                        return;
                    }

                    if (!"active".equals(
                            snapshot.getString("status"))) {

                        message("활성화된 은하계가 아닙니다.");
                        finish();
                        return;
                    }

                    String name =
                            snapshot.getString("name");

                    if (name == null) {
                        name = "";
                    }

                    currentName = name;
                    currentNameView.setText(name);

                    // 처음 한 번만 입력칸에 기존 이름 표시
                    if (!initialized) {
                        initialized = true;
                        editName.setText(name);
                        editName.setSelection(
                                editName.length()
                        );
                    }

                    refreshButton();
                }
        );
    }

    // ==========================================
    // 은하계 이름 저장
    // ==========================================

    private void saveName() {

        if (saving || galaxyRef == null
                || currentName == null) {
            return;
        }

        String newName = editName
                .getText()
                .toString()
                .trim();

        String error =
                CoupleInputValidator.validateGalaxyName(
                        newName
                );

        if (error != null) {
            editName.setError(error);
            return;
        }

        if (TextUtils.equals(newName, currentName)) {
            message("기존 이름과 동일합니다.");
            return;
        }

        saving = true;
        editName.setEnabled(false);
        btnSave.setText("변경 중...");
        refreshButton();

        // name 필드만 변경
        galaxyRef.update("name", newName)
                .addOnSuccessListener(this, unused -> {

                    saving = false;

                    message("은하계 이름이 변경되었습니다.");

                    finish();
                })
                .addOnFailureListener(this, e -> {

                    saving = false;
                    editName.setEnabled(true);
                    btnSave.setText(
                            "은하계 이름 변경하기"
                    );
                    refreshButton();

                    message(
                            "이름 변경 실패: "
                                    + e.getMessage()
                    );
                });
    }

    private void refreshButton() {

        if (btnSave == null) return;

        boolean enabled =
                !saving
                        && galaxyRef != null
                        && currentName != null;

        btnSave.setEnabled(enabled);
        btnSave.setAlpha(enabled ? 1f : 0.5f);
    }

    // ==========================================
    // UI 공통 함수
    // ==========================================

    private TextView createText(
            String text,
            int size,
            int color,
            boolean bold) {

        TextView view = new TextView(this);

        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);

        if (bold) {
            view.setTypeface(
                    null,
                    Typeface.BOLD
            );
        }

        return view;
    }

    private GradientDrawable roundedBackground(
            int color,
            int strokeColor) {

        GradientDrawable drawable =
                new GradientDrawable();

        drawable.setColor(color);
        drawable.setCornerRadius(dp(14));

        if (strokeColor != 0) {
            drawable.setStroke(
                    dp(1),
                    strokeColor
            );
        }

        return drawable;
    }

    private int dp(float value) {
        return Math.round(
                value * getResources()
                        .getDisplayMetrics().density
        );
    }

    private void message(String text) {
        Toast.makeText(
                this,
                text,
                Toast.LENGTH_SHORT
        ).show();
    }

    // ==========================================
    // 생명주기
    // ==========================================

    @Override
    protected void onStart() {
        super.onStart();
        startNameListener();
    }

    @Override
    protected void onStop() {

        if (nameListener != null) {
            nameListener.remove();
            nameListener = null;
        }

        super.onStop();
    }
}

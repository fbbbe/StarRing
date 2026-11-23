
package com.example.starring;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.example.starring.common.ErrorMessages;
import com.example.starring.common.Navigator;
import com.example.starring.common.StarFieldView;
import com.example.starring.data.GalaxyRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.Calendar;
import java.util.Locale;

public class GalaxySetupActivity extends Activity {

    public static final String EXTRA_EXISTING_GALAXY_ID =
            "existingGalaxyId";

    public static final String EXTRA_EXISTING_GALAXY_NAME =
            "existingGalaxyName";

    private EditText editGalaxyName;
    private Button btnDate;
    private Button btnCreate;
    private Button btnJoin;
    private TextView textError;

    private String selectedDate;
    private String existingGalaxyId;

    private final GalaxyRepository repository =
            new GalaxyRepository();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FirebaseUser user =
                FirebaseAuth.getInstance().getCurrentUser();

        if (user == null) {
            Navigator.toLogin(this);
            return;
        }

        existingGalaxyId = getIntent().getStringExtra(
                EXTRA_EXISTING_GALAXY_ID
        );

        String existingName = getIntent().getStringExtra(
                EXTRA_EXISTING_GALAXY_NAME
        );

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(13, 10, 35));

        // 기존 로그인 화면과 동일한 별 배경 활용
        root.addView(
                new StarFieldView(this),
                new FrameLayout.LayoutParams(-1, -1)
        );

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER);
        content.setPadding(
                dp(28), dp(40), dp(28), dp(40)
        );

        TextView title = new TextView(this);
        title.setText("우리만의 은하계");
        title.setTextSize(29);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER);
        content.addView(title);

        TextView description = new TextView(this);
        description.setText(
                "은하계 이름과 만난 날짜를 설정해주세요."
        );
        description.setTextSize(15);
        description.setTextColor(
                Color.rgb(205, 190, 231)
        );
        description.setGravity(Gravity.CENTER);
        description.setPadding(0, dp(15), 0, dp(35));
        content.addView(description);

        editGalaxyName = new EditText(this);
        editGalaxyName.setHint("은하계 이름");
        editGalaxyName.setSingleLine(true);
        editGalaxyName.setTextColor(Color.WHITE);
        editGalaxyName.setHintTextColor(Color.LTGRAY);
        editGalaxyName.setTextSize(17);
        content.addView(
                editGalaxyName,
                new LinearLayout.LayoutParams(-1, dp(60))
        );

        btnDate = new Button(this);
        btnDate.setText("만난 날짜 선택");
        btnDate.setOnClickListener(v -> showDatePicker());

        LinearLayout.LayoutParams dateParams =
                new LinearLayout.LayoutParams(-1, dp(55));
        dateParams.topMargin = dp(18);
        content.addView(btnDate, dateParams);

        btnCreate = new Button(this);
        btnCreate.setText(
                existingGalaxyId == null
                        ? "은하계 만들기"
                        : "만난 날짜 저장"
        );

        LinearLayout.LayoutParams createParams =
                new LinearLayout.LayoutParams(-1, dp(55));
        createParams.topMargin = dp(30);
        content.addView(btnCreate, createParams);

        btnJoin = new Button(this);
        btnJoin.setText("초대코드로 합류하기");

        LinearLayout.LayoutParams joinParams =
                new LinearLayout.LayoutParams(-1, dp(55));
        joinParams.topMargin = dp(12);
        content.addView(btnJoin, joinParams);

        textError = new TextView(this);
        textError.setTextColor(
                Color.rgb(255, 145, 167)
        );
        textError.setGravity(Gravity.CENTER);
        textError.setTextSize(14);

        LinearLayout.LayoutParams errorParams =
                new LinearLayout.LayoutParams(-1, -2);
        errorParams.topMargin = dp(15);
        content.addView(textError, errorParams);

        if (existingGalaxyId != null) {
            editGalaxyName.setText(
                    existingName == null ? "" : existingName
            );
            editGalaxyName.setEnabled(false);
            btnJoin.setVisibility(android.view.View.GONE);
        }

        btnCreate.setOnClickListener(v -> submit(user));

        btnJoin.setOnClickListener(
                v -> Navigator.toCoupleLink(this)
        );

        scroll.addView(content);
        root.addView(scroll);
        setContentView(root);
    }

    private void showDatePicker() {

        Calendar today = Calendar.getInstance();

        DatePickerDialog picker = new DatePickerDialog(
                this,
                (view, year, month, day) -> {

                    selectedDate = String.format(
                            Locale.US,
                            "%04d-%02d-%02d",
                            year, month + 1, day
                    );

                    btnDate.setText(
                            "만난 날짜: " + selectedDate
                    );
                },
                today.get(Calendar.YEAR),
                today.get(Calendar.MONTH),
                today.get(Calendar.DAY_OF_MONTH)
        );

        // 미래 날짜 선택 방지
        picker.getDatePicker().setMaxDate(
                System.currentTimeMillis()
        );

        picker.show();
    }

    private void submit(FirebaseUser user) {

        String name = editGalaxyName.getText()
                .toString().trim();

        if (name.isEmpty() || name.length() > 20) {
            textError.setText(
                    "은하계 이름은 1~20자로 입력해주세요."
            );
            return;
        }

        if (selectedDate == null) {
            textError.setText(
                    "만난 날짜를 선택해주세요."
            );
            return;
        }

        textError.setText("");
        btnCreate.setEnabled(false);
        btnJoin.setEnabled(false);

        // 기존 은하계에 날짜가 없는 경우
        if (existingGalaxyId != null) {

            repository.setDatingStartDateForExistingGalaxy(
                    user.getUid(),
                    existingGalaxyId,
                    selectedDate
            ).addOnSuccessListener(this, unused -> {
                Navigator.toMain(this);
            }).addOnFailureListener(this, e -> {
                btnCreate.setEnabled(true);
                textError.setText(
                        ErrorMessages.toMessage(e)
                );
            });

            return;
        }

        // 새 은하계 생성
        repository.createGalaxy(
                user.getUid(),
                name,
                selectedDate
        ).addOnSuccessListener(this, galaxyId -> {
            Navigator.toCoupleLink(this);
        }).addOnFailureListener(this, e -> {
            btnCreate.setEnabled(true);
            btnJoin.setEnabled(true);
            textError.setText(
                    ErrorMessages.toMessage(e)
            );
        });
    }

    private int dp(float value) {
        return Math.round(
                value * getResources()
                        .getDisplayMetrics().density
        );
    }
}

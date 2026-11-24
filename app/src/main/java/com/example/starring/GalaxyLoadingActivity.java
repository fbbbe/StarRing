
package com.example.starring;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.example.starring.common.ErrorMessages;
import com.example.starring.common.Navigator;
import com.example.starring.common.StarFieldView;
import com.example.starring.data.FirestoreSchema;
import com.example.starring.data.GalaxyRepository;
import com.example.starring.data.UserRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;

public class GalaxyLoadingActivity extends Activity {

    public static final String EXTRA_START_DATE =
            "datingStartDate";

    public static final String EXTRA_GALAXY_ID =
            "galaxyId";

    public static final String EXTRA_GALAXY_NAME =
            "galaxyName";

    private final UserRepository userRepository =
            new UserRepository();

    private final GalaxyRepository galaxyRepository =
            new GalaxyRepository();

    private final Handler handler =
            new Handler(Looper.getMainLooper());

    private TextView textStatus;
    private Button btnRetry;
    private long openedAt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        openedAt = SystemClock.elapsedRealtime();

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(12, 9, 34));

        root.addView(
                new StarFieldView(this),
                new FrameLayout.LayoutParams(-1, -1)
        );

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER);
        content.setPadding(30, 30, 30, 30);

        TextView title = new TextView(this);
        title.setText("StarRing");
        title.setTextAppearance(R.style.TextAppearance_StarRing_Title);
        title.setTextColor(Color.rgb(218, 188, 255));
        title.setGravity(Gravity.CENTER);
        content.addView(title);

        ProgressBar progress = new ProgressBar(this);

        LinearLayout.LayoutParams progressParams =
                new LinearLayout.LayoutParams(85, 85);
        progressParams.gravity = Gravity.CENTER;
        progressParams.topMargin = 35;
        content.addView(progress, progressParams);

        textStatus = new TextView(this);
        textStatus.setText("우리만의 은하계를 만드는 중...");
        textStatus.setTextSize(16);
        textStatus.setTextColor(Color.WHITE);
        textStatus.setGravity(Gravity.CENTER);
        textStatus.setPadding(0, 35, 0, 0);
        content.addView(textStatus);

        btnRetry = new Button(this);
        btnRetry.setText("다시 시도");
        btnRetry.setVisibility(android.view.View.GONE);
        btnRetry.setOnClickListener(v -> loadGalaxy());
        content.addView(btnRetry);

        root.addView(
                content,
                new FrameLayout.LayoutParams(-1, -1)
        );

        setContentView(root);
    }

    @Override
    protected void onStart() {
        super.onStart();
        loadGalaxy();
    }

    private void loadGalaxy() {

        btnRetry.setVisibility(android.view.View.GONE);
        textStatus.setText("은하계 정보를 불러오는 중...");

        FirebaseUser user =
                FirebaseAuth.getInstance().getCurrentUser();

        if (user == null) {
            Navigator.toLogin(this);
            return;
        }

        userRepository.getUser(user.getUid())
                .addOnSuccessListener(this, userDoc -> {

                    String galaxyId = userDoc.getString(
                            FirestoreSchema.USER_GALAXY_ID
                    );

                    if (galaxyId == null) {
                        Navigator.toGalaxySetup(this);
                        return;
                    }

                    fetchGalaxy(galaxyId);

                }).addOnFailureListener(
                        this, this::showError
                );
    }

    private void fetchGalaxy(String galaxyId) {

        galaxyRepository.getGalaxy(galaxyId)
                .addOnSuccessListener(this, galaxy -> {

                    if (!galaxy.exists()) {
                        Navigator.toGalaxySetup(this);
                        return;
                    }

                    String status = galaxy.getString(
                            FirestoreSchema.GALAXY_STATUS
                    );

                    if (!FirestoreSchema.GalaxyStatus.ACTIVE
                            .equals(status)) {

                        Navigator.toCoupleLink(this);
                        return;
                    }

                    String startDate = galaxy.getString(
                            FirestoreSchema.GALAXY_DATING_START_DATE
                    );

                    String galaxyName = galaxy.getString(
                            FirestoreSchema.GALAXY_NAME
                    );

                    // 기존 은하계에 만난 날짜가 없는 경우
                    if (startDate == null
                            || !startDate.matches(
                            "\\d{4}-\\d{2}-\\d{2}")) {

                        Intent setup = new Intent(
                                this, GalaxySetupActivity.class
                        );

                        setup.putExtra(
                                GalaxySetupActivity
                                        .EXTRA_EXISTING_GALAXY_ID,
                                galaxyId
                        );

                        setup.putExtra(
                                GalaxySetupActivity
                                        .EXTRA_EXISTING_GALAXY_NAME,
                                galaxyName
                        );

                        setup.addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK
                                        | Intent.FLAG_ACTIVITY_CLEAR_TASK
                        );

                        startActivity(setup);
                        finish();
                        return;
                    }

                    textStatus.setText(
                            "우리만의 은하계로 이동합니다..."
                    );

                    // 화면이 너무 빨리 사라지지 않도록
                    // 최소 1.2초 표시
                    long elapsed =
                            SystemClock.elapsedRealtime()
                                    - openedAt;

                    long delay = Math.max(
                            0L, 1200L - elapsed
                    );

                    handler.postDelayed(() -> {
                        openGalaxy(
                                galaxyId,
                                galaxyName,
                                startDate
                        );
                    }, delay);

                }).addOnFailureListener(
                        this, this::showError
                );
    }

    private void openGalaxy(
            String galaxyId,
            String galaxyName,
            String startDate) {

        if (isFinishing() || isDestroyed()) {
            return;
        }

        Intent intent = new Intent(
                this, GalaxyActivity.class
        );

        intent.putExtra(
                EXTRA_GALAXY_ID, galaxyId
        );

        intent.putExtra(
                EXTRA_GALAXY_NAME, galaxyName
        );

        intent.putExtra(
                EXTRA_START_DATE, startDate
        );

        intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK
        );

        startActivity(intent);
        finish();
    }

    private void showError(Exception e) {

        textStatus.setText(
                ErrorMessages.toMessage(e)
        );

        btnRetry.setVisibility(
                android.view.View.VISIBLE
        );
    }

    @Override
    protected void onStop() {
        handler.removeCallbacksAndMessages(null);
        super.onStop();
    }
}

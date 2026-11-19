package com.example.starring;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.example.starring.common.ErrorMessages;
import com.example.starring.common.InsetsHelper;
import com.example.starring.common.Navigator;
import com.example.starring.data.FirestoreSchema;
import com.example.starring.data.FirestoreSchema.GalaxyStatus;
import com.example.starring.data.GalaxyRepository;
import com.example.starring.data.UserRepository;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;

/**
 * Splash: 로그인 상태와 은하계 상태를 확인해서 알맞은 화면으로 보내는 역할만 한다.
 *
 * 로그인 안 됨                      → LoginActivity
 * 로그인 됨 + 은하계 없음            → CoupleLinkActivity
 * 로그인 됨 + 은하계 대기/이름설정 중 → CoupleLinkActivity (이어서 진행)
 * 로그인 됨 + 은하계 해제/삭제됨      → 내 galaxyId 정리 후 CoupleLinkActivity
 * 로그인 됨 + 은하계 active          → 메인 화면
 */
public class MainActivity extends AppCompatActivity {

    private final UserRepository userRepository = new UserRepository();
    private final GalaxyRepository galaxyRepository = new GalaxyRepository();

    private ProgressBar progress;
    private TextView textError;
    private View errorActions;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_splash);
        InsetsHelper.applySystemBarsAndIme(findViewById(R.id.content));

        progress = findViewById(R.id.progress);
        textError = findViewById(R.id.textError);
        errorActions = findViewById(R.id.errorActions);
        MaterialButton btnRetry = findViewById(R.id.btnRetry);
        MaterialButton btnLogout = findViewById(R.id.btnLogout);

        btnRetry.setOnClickListener(v -> route());
        btnLogout.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            Navigator.toLogin(this);
        });

        route();
    }

    private void route() {
        showLoading();

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Navigator.toLogin(this);
            return;
        }

        userRepository.getUser(user.getUid())
                .addOnSuccessListener(this, userDoc -> {
                    if (!userDoc.exists()) {
                        recoverUserDocument(user);
                        return;
                    }
                    String galaxyId = userDoc.getString(FirestoreSchema.USER_GALAXY_ID);
                    if (galaxyId == null) {
                        Navigator.toCoupleLink(this);
                    } else {
                        checkGalaxy(user.getUid(), galaxyId);
                    }
                })
                .addOnFailureListener(this, this::showError);
    }

    /** 회원가입 때 users 문서 저장이 실패한 경우 복구. 닉네임은 Auth displayName에서 가져온다. */
    private void recoverUserDocument(FirebaseUser user) {
        String nickname = user.getDisplayName();
        if (nickname == null || nickname.trim().isEmpty()) {
            String email = user.getEmail() == null ? "" : user.getEmail();
            nickname = email.contains("@") ? email.substring(0, email.indexOf('@')) : "별지기";
        }
        userRepository.createUser(user.getUid(), user.getEmail(), nickname)
                .addOnSuccessListener(this, unused -> Navigator.toCoupleLink(this))
                .addOnFailureListener(this, this::showError);
    }

    private void checkGalaxy(String uid, String galaxyId) {
        galaxyRepository.getGalaxy(galaxyId)
                .addOnSuccessListener(this, galaxyDoc -> {
                    String status = galaxyDoc.exists()
                            ? galaxyDoc.getString(FirestoreSchema.GALAXY_STATUS)
                            : null;

                    if (GalaxyStatus.ACTIVE.equals(status)) {
                        Navigator.toMain(this);
                    } else if (status == null || GalaxyStatus.UNLINKED.equals(status)) {
                        // 상대가 연동을 해제했거나 은하계가 삭제됨 → 내 문서만 정리
                        userRepository.clearGalaxyId(uid)
                                .addOnSuccessListener(this, unused -> Navigator.toCoupleLink(this))
                                .addOnFailureListener(this, this::showError);
                    } else {
                        // waiting / naming → 커플 연동 화면에서 이어서 진행
                        Navigator.toCoupleLink(this);
                    }
                })
                .addOnFailureListener(this, this::showError);
    }

    private void showLoading() {
        progress.setVisibility(View.VISIBLE);
        textError.setVisibility(View.GONE);
        errorActions.setVisibility(View.GONE);
    }

    private void showError(Exception e) {
        progress.setVisibility(View.GONE);
        textError.setText(ErrorMessages.toMessage(e));
        textError.setVisibility(View.VISIBLE);
        errorActions.setVisibility(View.VISIBLE);
    }
}

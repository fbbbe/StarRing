package com.example.starring.auth;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.example.starring.R;
import com.example.starring.common.InsetsHelper;
import com.example.starring.common.Navigator;
import com.example.starring.data.UserRepository;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;

public class SignUpActivity extends AppCompatActivity {

    private TextInputLayout layoutEmail, layoutPassword, layoutPasswordConfirm, layoutNickname;
    private TextInputEditText editEmail, editPassword, editPasswordConfirm, editNickname;
    private MaterialButton btnSignUp, btnBack;
    private ProgressBar progress;
    private TextView textError;

    private final UserRepository userRepository = new UserRepository();

    /** 가입 처리 중에는 뒤로가기 막기 (중간 상태 방지). 로딩 중에만 enabled. */
    private final OnBackPressedCallback blockBackWhileLoading = new OnBackPressedCallback(false) {
        @Override
        public void handleOnBackPressed() {
            // 아무것도 하지 않음
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_sign_up);
        InsetsHelper.applySystemBarsAndIme(findViewById(R.id.content));

        layoutEmail = findViewById(R.id.layoutEmail);
        layoutPassword = findViewById(R.id.layoutPassword);
        layoutPasswordConfirm = findViewById(R.id.layoutPasswordConfirm);
        layoutNickname = findViewById(R.id.layoutNickname);
        editEmail = findViewById(R.id.editEmail);
        editPassword = findViewById(R.id.editPassword);
        editPasswordConfirm = findViewById(R.id.editPasswordConfirm);
        editNickname = findViewById(R.id.editNickname);
        btnSignUp = findViewById(R.id.btnSignUp);
        btnBack = findViewById(R.id.btnBack);
        progress = findViewById(R.id.progress);
        textError = findViewById(R.id.textError);

        btnSignUp.setOnClickListener(v -> signUp());
        btnBack.setOnClickListener(v -> finish());
        getOnBackPressedDispatcher().addCallback(this, blockBackWhileLoading);
    }

    private void signUp() {
        String email = text(editEmail).trim();
        String password = text(editPassword);
        String confirm = text(editPasswordConfirm);
        String nickname = text(editNickname).trim();

        String emailError = InputValidator.validateEmail(email);
        String passwordError = InputValidator.validateNewPassword(password);
        String confirmError = InputValidator.validatePasswordConfirm(password, confirm);
        String nicknameError = InputValidator.validateNickname(nickname);
        layoutEmail.setError(emailError);
        layoutPassword.setError(passwordError);
        layoutPasswordConfirm.setError(confirmError);
        layoutNickname.setError(nicknameError);
        textError.setVisibility(View.GONE);
        if (emailError != null || passwordError != null
                || confirmError != null || nicknameError != null) {
            return;
        }

        setLoading(true);
        FirebaseAuth.getInstance()
                .createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (!task.isSuccessful() || task.getResult().getUser() == null) {
                        setLoading(false);
                        showError(AuthErrorMapper.toMessage(task.getException()));
                        return;
                    }
                    saveProfile(task.getResult().getUser(), email, nickname);
                });
    }

    /**
     * Auth 프로필 displayName에도 닉네임을 넣어둔다.
     * → users 문서 저장이 실패해도 Splash가 displayName으로 문서를 복구할 수 있다.
     */
    private void saveProfile(FirebaseUser user, String email, String nickname) {
        UserProfileChangeRequest profile = new UserProfileChangeRequest.Builder()
                .setDisplayName(nickname)
                .build();

        user.updateProfile(profile)
                // displayName 실패 여부와 상관없이 users 문서 저장 진행
                .continueWithTask(t -> userRepository.createUser(user.getUid(), email, nickname))
                .addOnCompleteListener(this, t -> {
                    if (t.isSuccessful()) {
                        // 갓 가입한 사용자는 은하계가 없으므로 바로 커플 연동으로
                        Navigator.toCoupleLink(this);
                    } else {
                        // 계정은 이미 만들어졌으므로 Splash에서 users 문서 복구 후 분기
                        Navigator.toSplash(this);
                    }
                });
    }

    private void setLoading(boolean loading) {
        btnSignUp.setEnabled(!loading);
        btnBack.setEnabled(!loading);
        editEmail.setEnabled(!loading);
        editPassword.setEnabled(!loading);
        editPasswordConfirm.setEnabled(!loading);
        editNickname.setEnabled(!loading);
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        blockBackWhileLoading.setEnabled(loading);
    }

    private void showError(String message) {
        textError.setText(message);
        textError.setVisibility(View.VISIBLE);
    }

    private static String text(TextInputEditText edit) {
        return edit.getText() == null ? "" : edit.getText().toString();
    }
}

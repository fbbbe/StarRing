package com.example.starring.auth;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import androidx.lifecycle.ViewModelProvider;

import com.example.starring.R;
import com.example.starring.common.InsetsHelper;
import com.example.starring.common.Navigator;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;

public class SignUpActivity extends AppCompatActivity {

    private TextInputLayout layoutEmail, layoutPassword, layoutPasswordConfirm, layoutNickname;
    private TextInputEditText editEmail, editPassword, editPasswordConfirm, editNickname;
    private MaterialButton btnSignUp, btnBack;
    private ProgressBar progress;
    private TextView textError;

    private AuthViewModel viewModel;

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

        // 회전 후 다시 만들어져도 같은 ViewModel → 진행 중/완료 상태를 이어받는다
        viewModel = new ViewModelProvider(this).get(AuthViewModel.class);
        viewModel.state().observe(this, this::render);
    }

    private void render(AuthUiState state) {
        switch (state.status) {
            case LOADING:
                setLoading(true);
                break;
            case SUCCESS:
            case SUCCESS_NEEDS_RECOVERY:
                FirebaseAuth.getInstance().signOut();
                Navigator.toLogin(this);
                break;
            case ERROR:
                setLoading(false);
                showError(state.message);
                break;
            default:
                setLoading(false);
        }
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

        viewModel.signUp(email, password, nickname);
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

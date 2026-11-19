package com.example.starring.auth;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import androidx.lifecycle.ViewModelProvider;

import com.example.starring.R;
import com.example.starring.common.InsetsHelper;
import com.example.starring.common.Navigator;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class LoginActivity extends AppCompatActivity {

    private TextInputLayout layoutEmail, layoutPassword;
    private TextInputEditText editEmail, editPassword;
    private MaterialButton btnLogin, btnGoSignUp;
    private ProgressBar progress;
    private TextView textError;

    private AuthViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);
        InsetsHelper.applySystemBarsAndIme(findViewById(R.id.content));

        layoutEmail = findViewById(R.id.layoutEmail);
        layoutPassword = findViewById(R.id.layoutPassword);
        editEmail = findViewById(R.id.editEmail);
        editPassword = findViewById(R.id.editPassword);
        btnLogin = findViewById(R.id.btnLogin);
        btnGoSignUp = findViewById(R.id.btnGoSignUp);
        progress = findViewById(R.id.progress);
        textError = findViewById(R.id.textError);

        btnLogin.setOnClickListener(v -> login());
        btnGoSignUp.setOnClickListener(v -> Navigator.toSignUp(this));

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
                // 은하계 유무에 따른 분기는 Splash(MainActivity)가 담당
                Navigator.toSplash(this);
                break;
            case ERROR:
                setLoading(false);
                showError(state.message);
                break;
            default:
                setLoading(false);
        }
    }

    private void login() {
        String email = text(editEmail).trim();
        String password = text(editPassword);

        String emailError = InputValidator.validateEmail(email);
        String passwordError = InputValidator.validateLoginPassword(password);
        layoutEmail.setError(emailError);
        layoutPassword.setError(passwordError);
        textError.setVisibility(View.GONE);
        if (emailError != null || passwordError != null) {
            return;
        }

        viewModel.login(email, password);
    }

    private void setLoading(boolean loading) {
        btnLogin.setEnabled(!loading);
        btnGoSignUp.setEnabled(!loading);
        editEmail.setEnabled(!loading);
        editPassword.setEnabled(!loading);
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void showError(String message) {
        textError.setText(message);
        textError.setVisibility(View.VISIBLE);
    }

    private static String text(TextInputEditText edit) {
        return edit.getText() == null ? "" : edit.getText().toString();
    }
}

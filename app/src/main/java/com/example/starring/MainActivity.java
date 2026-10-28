
package com.example.starring;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Source;

import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private EditText editEmail, editPassword;
    private TextView textResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        editEmail = findViewById(R.id.editEmail);
        editPassword = findViewById(R.id.editPassword);
        textResult = findViewById(R.id.textResult);

        Button btnRegister = findViewById(R.id.btnRegister);
        Button btnLogin = findViewById(R.id.btnLogin);
        Button btnTest = findViewById(R.id.btnTest);

        btnRegister.setOnClickListener(v -> authenticate(true));
        btnLogin.setOnClickListener(v -> authenticate(false));
        btnTest.setOnClickListener(v -> testFirestore());

        if (auth.getCurrentUser() != null) {
            textResult.setText("이미 로그인된 상태입니다.");
        }
    }

    private void authenticate(boolean register) {

        String email = editEmail.getText().toString().trim();
        String password = editPassword.getText().toString();

        if (email.isEmpty() || password.length() < 6) {
            textResult.setText("이메일과 6자 이상 비밀번호를 입력하세요.");
            return;
        }

        Task<AuthResult> task = register
                ? auth.createUserWithEmailAndPassword(email, password)
                : auth.signInWithEmailAndPassword(email, password);

        task.addOnCompleteListener(this, result -> {
            if (result.isSuccessful()) {
                textResult.setText(register
                        ? "회원가입 성공!"
                        : "로그인 성공!");
            } else {
                textResult.setText("인증 실패: "
                        + result.getException().getMessage());
            }
        });
    }

    private void testFirestore() {

        FirebaseUser user = auth.getCurrentUser();

        if (user == null) {
            textResult.setText("먼저 로그인하세요.");
            return;
        }

        String uid = user.getUid();

        Map<String, Object> data = new HashMap<>();
        data.put("message", "StarRing 연결 성공");
        data.put("uid", uid);

        db.collection("connection_tests")
                .document(uid)
                .set(data)
                .addOnSuccessListener(unused -> {

                    textResult.setText("저장 성공! 조회 중...");

                    db.collection("connection_tests")
                            .document(uid)
                            .get(Source.SERVER)
                            .addOnSuccessListener(document -> {
                                String message = document.getString("message");
                                textResult.setText(
                                        "Firestore 연결 성공!\n" + message
                                );
                            })
                            .addOnFailureListener(e ->
                                    textResult.setText("조회 실패: " + e.getMessage())
                            );

                })
                .addOnFailureListener(e ->
                        textResult.setText("저장 실패: " + e.getMessage())
                );
    }
}

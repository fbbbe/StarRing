package com.example.starring.couple;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.example.starring.R;
import com.example.starring.common.InsetsHelper;
import com.example.starring.common.Navigator;
import com.google.firebase.auth.FirebaseAuth;

/**
 * 커플 연동 화면.
 * TODO(2단계): 은하계 만들기 / 초대코드 입력 / 대기 / 이름 설정 구현.
 * 지금은 1단계 분기 확인용 임시 화면.
 */
public class CoupleLinkActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_couple_link);
        InsetsHelper.applySystemBarsAndIme(findViewById(R.id.content));

        findViewById(R.id.btnLogout).setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            Navigator.toLogin(this);
        });
    }
}

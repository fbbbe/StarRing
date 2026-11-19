package com.example.starring;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public class PlanetCreateActivity extends Activity {

    private TextView btnBack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_planet_create);

        btnBack = findViewById(R.id.btnBack);

        // 뒤로가기 → 기존 은하계 화면 복귀
        btnBack.setOnClickListener(v -> {
            finish();
        });
    }
}

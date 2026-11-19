
package com.example.starring;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class CourtMainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setPadding(32, 32, 32, 32);
        layout.setBackgroundColor(
                Color.rgb(18, 13, 39)
        );

        TextView title = new TextView(this);
        title.setText("우주 재판장");
        title.setTextSize(28);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER);

        layout.addView(title);

        TextView description = new TextView(this);
        description.setText(
                "우주 재판장 메인 화면\n임시 테스트 페이지"
        );
        description.setTextSize(16);
        description.setTextColor(
                Color.rgb(205, 183, 235)
        );
        description.setGravity(Gravity.CENTER);
        description.setPadding(0, 32, 0, 32);

        layout.addView(description);

        Button btnWrite = new Button(this);
        btnWrite.setText("블랙홀 생성");

        btnWrite.setOnClickListener(v -> {
            Intent intent = new Intent(
                    this, CourtWriteActivity.class
            );
            startActivity(intent);
        });

        layout.addView(btnWrite);

        Button btnBack = new Button(this);
        btnBack.setText("메인으로 돌아가기");
        btnBack.setOnClickListener(v -> finish());

        layout.addView(btnBack);

        setContentView(layout);
    }
}

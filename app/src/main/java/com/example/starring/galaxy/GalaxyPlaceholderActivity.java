package com.example.starring.galaxy;

import android.os.Bundle;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.example.starring.R;
import com.example.starring.common.InsetsHelper;
import com.example.starring.common.Navigator;
import com.example.starring.data.FirestoreSchema;
import com.example.starring.data.GalaxyRepository;
import com.example.starring.data.UserRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * 임시 메인 화면. 팀원의 은하계 메인 화면이 완성되면 삭제하고
 * {@link Navigator#toMain} 의 대상만 바꾸면 된다.
 */
public class GalaxyPlaceholderActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_galaxy_placeholder);
        InsetsHelper.applySystemBarsAndIme(findViewById(R.id.content));

        TextView textGalaxyName = findViewById(R.id.textGalaxyName);
        findViewById(R.id.btnLogout).setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            Navigator.toLogin(this);
        });

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Navigator.toLogin(this);
            return;
        }
        new UserRepository().getUser(user.getUid())
                .continueWithTask(t -> {
                    String galaxyId = t.getResult().getString(FirestoreSchema.USER_GALAXY_ID);
                    return new GalaxyRepository().getGalaxy(galaxyId);
                })
                .addOnSuccessListener(this, galaxy -> {
                    String name = galaxy.getString(FirestoreSchema.GALAXY_NAME);
                    if (name != null) {
                        textGalaxyName.setText(getString(R.string.galaxy_name_format, name));
                    }
                });
    }
}

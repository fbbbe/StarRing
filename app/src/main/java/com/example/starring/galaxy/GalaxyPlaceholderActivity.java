package com.example.starring.galaxy;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.example.starring.R;
import com.example.starring.common.InsetsHelper;
import com.example.starring.common.Navigator;
import com.example.starring.couple.UnlinkHelper;
import com.example.starring.data.FirestoreSchema;
import com.example.starring.data.GalaxyRepository;
import com.example.starring.data.UserRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;

/**
 * 임시 메인 화면. 팀원의 은하계 메인 화면이 완성되면 삭제하고
 * {@link Navigator#toMain} 의 대상만 바꾸면 된다.
 * 연동 해제 / 상대 해제 감지는 {@link UnlinkHelper} 사용법 예시이기도 함.
 */
public class GalaxyPlaceholderActivity extends AppCompatActivity {

    private TextView textGalaxyName;
    private View btnUnlink;

    private String uid;
    private String galaxyId;
    private ListenerRegistration partnerUnlinkWatcher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_galaxy_placeholder);
        InsetsHelper.applySystemBarsAndIme(findViewById(R.id.content));

        textGalaxyName = findViewById(R.id.textGalaxyName);
        btnUnlink = findViewById(R.id.btnUnlink);
        btnUnlink.setEnabled(false);

        findViewById(R.id.btnLogout).setOnClickListener(v -> {
            stopWatcher();
            FirebaseAuth.getInstance().signOut();
            Navigator.toLogin(this);
        });
        btnUnlink.setOnClickListener(v -> {
            if (galaxyId != null) {
                UnlinkHelper.confirmAndUnlink(this, uid, galaxyId, this::stopWatcher);
            }
        });

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Navigator.toLogin(this);
            return;
        }
        uid = user.getUid();

        new UserRepository().getUser(uid)
                .continueWithTask(t -> {
                    galaxyId = t.getResult().getString(FirestoreSchema.USER_GALAXY_ID);
                    return new GalaxyRepository().getGalaxy(galaxyId);
                })
                .addOnSuccessListener(this, galaxy -> {
                    String name = galaxy.getString(FirestoreSchema.GALAXY_NAME);
                    if (name != null) {
                        textGalaxyName.setText(getString(R.string.galaxy_name_format, name));
                    }
                    btnUnlink.setEnabled(true);
                    startWatcher();
                });
    }

    @Override
    protected void onStart() {
        super.onStart();
        startWatcher();
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopWatcher();
    }

    private void startWatcher() {
        if (partnerUnlinkWatcher == null && galaxyId != null) {
            partnerUnlinkWatcher = UnlinkHelper.watchPartnerUnlink(this, uid, galaxyId);
        }
    }

    private void stopWatcher() {
        if (partnerUnlinkWatcher != null) {
            partnerUnlinkWatcher.remove();
            partnerUnlinkWatcher = null;
        }
    }
}

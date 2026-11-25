package com.example.starring.couple;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.example.starring.R;
import com.example.starring.common.ErrorMessages;
import com.example.starring.common.InsetsHelper;
import com.example.starring.common.Navigator;
import com.example.starring.data.FirestoreSchema;
import com.example.starring.data.FirestoreSchema.GalaxyStatus;
import com.example.starring.data.GalaxyRepository;
import com.example.starring.data.UserRepository;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 커플 연동 화면. 은하계 상태에 따라 패널을 바꿔 보여준다.
 *
 *  CHOOSE  : 은하계 없음 → "새 은하계 만들기"(만난 날 선택 후 생성) 또는 "초대코드로 합류"
 *  WAITING : 내가 만든 은하계에서 상대 대기 (코드 표시, 실시간 감지)
 *  NAMING  : 두 명이 모임 → 은하계 이름 설정 (먼저 저장한 사람의 이름 적용)
 *  ACTIVE  → 메인 화면으로 이동
 *
 * 은하계 문서를 snapshot listener로 감시하므로 상대의 합류/이름설정/해제가 바로 반영된다.
 */
public class CoupleLinkActivity extends AppCompatActivity {

    private enum Panel { LOADING, CHOOSE, WAITING, NAMING }


    private final UserRepository userRepository = new UserRepository();
    private final GalaxyRepository galaxyRepository = new GalaxyRepository();

    private View panelChoose, panelWaiting, panelNaming;
    private ProgressBar progress;
    private TextView textError;

    // CHOOSE
    private MaterialButton btnCreate, btnJoin;
    private TextInputLayout layoutCode;
    private TextInputEditText editCode;

    // WAITING
    private TextView textCode, textCodeExpiry;
    private MaterialButton btnCopyCode, btnShareCode, btnNewCode, btnCancelWaiting;

    // NAMING
    private TextView textPartner;
    private TextInputLayout layoutGalaxyName;
    private TextInputEditText editGalaxyName;
    private MaterialButton btnSaveName, btnUnlinkNaming;

    private String uid;
    private String galaxyId;
    /** users/{uid} 실시간 감시 → galaxyId가 바뀌면(생성/합류/해제, 다른 기기 포함) 바로 반영 */
    private ListenerRegistration userListener;
    private ListenerRegistration galaxyListener;
    /** 현재 화면에 표시 중인 초대코드 (코드가 바뀔 때만 유효기간을 다시 조회) */
    private String shownCode;
    private String partnerUidShown;
    private boolean busy;
    private boolean leaving;
    private boolean autoReissueTried;
    /** 내가 취소/해제하는 중 → "상대가 해제했어요" 안내를 띄우지 않기 위함 */
    private boolean selfLeaving;
    private boolean activationInFlight = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_couple_link);
        InsetsHelper.applySystemBarsAndIme(findViewById(R.id.content));

        panelChoose = findViewById(R.id.panelChoose);
        panelWaiting = findViewById(R.id.panelWaiting);
        panelNaming = findViewById(R.id.panelNaming);
        progress = findViewById(R.id.progress);
        textError = findViewById(R.id.textError);

        btnCreate = findViewById(R.id.btnCreate);
        btnJoin = findViewById(R.id.btnJoin);
        layoutCode = findViewById(R.id.layoutCode);
        editCode = findViewById(R.id.editCode);
        editCode.setFilters(InviteCodeInputFilter.filters());

        textCode = findViewById(R.id.textCode);
        textCodeExpiry = findViewById(R.id.textCodeExpiry);
        btnCopyCode = findViewById(R.id.btnCopyCode);
        btnShareCode = findViewById(R.id.btnShareCode);
        btnNewCode = findViewById(R.id.btnNewCode);
        btnCancelWaiting = findViewById(R.id.btnCancelWaiting);

        textPartner = findViewById(R.id.textPartner);
        layoutGalaxyName = findViewById(R.id.layoutGalaxyName);
        editGalaxyName = findViewById(R.id.editGalaxyName);
        btnSaveName = findViewById(R.id.btnSaveName);
        btnUnlinkNaming = findViewById(R.id.btnUnlinkNaming);

        btnCreate.setOnClickListener(
                v -> Navigator.toGalaxySetup(this)
        );
        btnJoin.setOnClickListener(v -> joinGalaxy());
        btnCopyCode.setOnClickListener(v -> copyCode());
        btnShareCode.setOnClickListener(v -> shareCode());
        btnNewCode.setOnClickListener(v -> reissueCode());
        btnCancelWaiting.setOnClickListener(v -> confirmCancelWaiting());
        btnSaveName.setOnClickListener(v -> saveName());
        btnUnlinkNaming.setOnClickListener(v -> confirmUnlinkWhileNaming());
        findViewById(R.id.btnLogout).setOnClickListener(v -> {
            stopUserListener();
            stopWatching();
            FirebaseAuth.getInstance().signOut();
            Navigator.toLogin(this);
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Navigator.toLogin(this);
            return;
        }
        uid = user.getUid();
        startUserListener();
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopUserListener();
        stopWatching();
    }

    // ───────────────────────── 상태 로딩 / 감시 ─────────────────────────

    /**
     * 한 번 읽기(get) 대신 실시간 감시를 쓰는 이유:
     * 은하계 생성/합류 도중 화면이 회전돼 Activity가 다시 만들어져도,
     * 트랜잭션이 끝나 galaxyId가 기록되는 순간 새 Activity가 그 변화를 받는다.
     */
    private void startUserListener() {
        stopUserListener();
        show(Panel.LOADING);
        userListener = userRepository.userRef(uid).addSnapshotListener((doc, e) -> {
            if (leaving) {
                return;
            }
            if (e != null) {
                show(Panel.CHOOSE);
                showError(ErrorMessages.toMessage(e));
                return;
            }
            String gid = doc == null ? null : doc.getString(FirestoreSchema.USER_GALAXY_ID);
            if (gid == null) {
                stopWatching();
                galaxyId = null;
                shownCode = null;
                partnerUidShown = null;
                show(Panel.CHOOSE);
            } else {
                watch(gid);
            }
        });
    }

    private void stopUserListener() {
        if (userListener != null) {
            userListener.remove();
            userListener = null;
        }
    }

    private void watch(String gid) {
        if (gid.equals(galaxyId) && galaxyListener != null) {
            return; // 이미 감시 중
        }
        stopWatching();
        galaxyId = gid;
        galaxyListener = galaxyRepository.watchGalaxy(gid, (snap, e) -> {
            if (e != null) {
                if (GalaxyRepository.isPermissionDenied(e)) {
                    // 상대가 해제하면 rules상 은하계를 조회할 수 없음 → 해제된 것으로 처리
                    handleUnlinked();
                } else {
                    showError(ErrorMessages.toMessage(e));
                }
                return;
            }
            if (snap != null) {
                render(snap);
            }
        });
    }

    private void stopWatching() {
        if (galaxyListener != null) {
            galaxyListener.remove();
            galaxyListener = null;
        }
    }

    private void render(DocumentSnapshot galaxy) {
        if (leaving) {
            return;
        }
        if (!galaxy.exists()) {
            // 대기 중 은하계가 삭제됨 → 내 galaxyId 정리
            stopWatching();
            galaxyId = null;
            userRepository.clearGalaxyId(uid);
            show(Panel.CHOOSE);
            return;
        }

        String status = galaxy.getString(FirestoreSchema.GALAXY_STATUS);
        if (GalaxyStatus.WAITING.equals(status)) {
            showWaiting(galaxy.getString(FirestoreSchema.GALAXY_INVITE_CODE));
        } else if (GalaxyStatus.NAMING.equals(status)) {
            activateAfterLink();
        } else if (GalaxyStatus.ACTIVE.equals(status)) {
            goMain();
        } else if (GalaxyStatus.UNLINKED.equals(status)) {
            handleUnlinked();
        }
    }

    /** 이름을 정하기 전에 상대가 연결을 취소함 (상태가 unlinked이거나, 해제돼서 조회가 거부됨) → 내 galaxyId 정리 */
    private void handleUnlinked() {
        if (leaving) {
            return;
        }
        stopWatching();
        galaxyId = null;
        userRepository.clearGalaxyId(uid);
        if (!selfLeaving) {
            Toast.makeText(this, R.string.unlink_by_partner, Toast.LENGTH_LONG).show();
        }
        show(Panel.CHOOSE);
    }

    private void goMain() {
        if (leaving) {
            return;
        }
        leaving = true;
        stopWatching();
        Navigator.toMain(this);
    }

    // ───────────────────────── CHOOSE ─────────────────────────


    private void joinGalaxy() {
        clearError();
        String code = InviteCode.normalize(text(editCode));
        String codeError = CoupleInputValidator.validateInviteCode(code);
        layoutCode.setError(codeError);
        if (codeError != null) {
            return;
        }

        setBusy(true);
        galaxyRepository.joinGalaxy(uid, code)
                .addOnSuccessListener(this, gid -> {
                    setBusy(false);
                    editCode.setText("");
                    watch(gid);
                })
                .addOnFailureListener(this, e -> {
                    setBusy(false);
                    layoutCode.setError(ErrorMessages.toMessage(e));
                });
    }

    // ───────────────────────── WAITING ─────────────────────────

    private void showWaiting(String code) {
        show(Panel.WAITING);
        if (code == null) {
            textCode.setText("------");
            return;
        }
        textCode.setText(code);
        if (!code.equals(shownCode)) {
            shownCode = code;
            textCodeExpiry.setText(R.string.code_expiry_loading);
            loadCodeExpiry(code);
        }
    }

    /** 초대코드 유효기간 표시. createdAt(serverTimestamp) + 24시간. */
    private void loadCodeExpiry(String code) {
        galaxyRepository.getInviteCode(code)
                .addOnSuccessListener(this, doc -> {
                    if (!code.equals(shownCode)) {
                        return; // 그 사이 코드가 바뀜
                    }
                    Timestamp createdAt = doc.getTimestamp(FirestoreSchema.CODE_CREATED_AT);
                    if (createdAt == null) {
                        // serverTimestamp가 아직 확정되지 않음 → 표시하지 않음
                        textCodeExpiry.setText(R.string.code_expiry_loading);
                        return;
                    }
                    long expiresAt = createdAt.toDate().getTime() + InviteCode.VALID_MILLIS;
                    SimpleDateFormat fmt = new SimpleDateFormat("M월 d일 HH:mm", Locale.KOREA);
                    textCodeExpiry.setText(getString(R.string.code_expiry_format,
                            fmt.format(new Date(expiresAt))));

                    // 만료됐으면 한 번 자동 재발급.
                    // 기기 시간은 "재발급할지" 판단하는 편의용으로만 쓰고 저장하지 않는다.
                    // 실제 만료 판단은 firestore.rules의 request.time(서버 시각)이 한다.
                    if (System.currentTimeMillis() > expiresAt && !autoReissueTried) {
                        autoReissueTried = true;
                        reissueCode();
                    }
                });
    }

    private void copyCode() {
        if (shownCode == null) {
            return;
        }
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("StarRing 초대코드", shownCode));
        Toast.makeText(this, R.string.code_copied, Toast.LENGTH_SHORT).show();
    }

    private void shareCode() {
        if (shownCode == null) {
            return;
        }
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, getString(R.string.code_share_text, shownCode));
        startActivity(Intent.createChooser(send, getString(R.string.action_share_code)));
    }

    private void reissueCode() {
        if (galaxyId == null) {
            return;
        }
        clearError();
        setBusy(true);
        galaxyRepository.reissueInviteCode(uid, galaxyId)
                .addOnCompleteListener(this, t -> {
                    setBusy(false);
                    if (t.isSuccessful()) {
                        Toast.makeText(this, R.string.code_reissued, Toast.LENGTH_SHORT).show();
                    } else {
                        showError(ErrorMessages.toMessage(t.getException()));
                    }
                    // 화면 갱신은 리스너가 담당
                });
    }

    private void confirmCancelWaiting() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.cancel_waiting_title)
                .setMessage(R.string.cancel_waiting_message)
                .setNegativeButton(R.string.action_keep_waiting, null)
                .setPositiveButton(R.string.action_cancel_waiting, (d, w) -> cancelWaiting())
                .show();
    }

    private void cancelWaiting() {
        if (galaxyId == null) {
            return;
        }
        clearError();
        setBusy(true);
        String gid = galaxyId;
        selfLeaving = true;
        stopWatching(); // 삭제 이벤트를 리스너가 따로 처리하지 않도록
        galaxyRepository.cancelWaiting(uid, gid)
                .addOnSuccessListener(this, unused -> {
                    selfLeaving = false;
                    setBusy(false);
                    galaxyId = null;
                    shownCode = null;
                    show(Panel.CHOOSE);
                })
                .addOnFailureListener(this, e -> {
                    selfLeaving = false;
                    setBusy(false);
                    showError(ErrorMessages.toMessage(e));
                    watch(gid); // 상대가 이미 합류했으면 리스너가 이름 설정 화면으로 보냄
                });
    }

    // ───────────────────────── NAMING ─────────────────────────

    private void showNaming(DocumentSnapshot galaxy) {
        show(Panel.NAMING);
        List<String> members = GalaxyRepository.memberUids(galaxy);
        String partnerUid = null;
        for (String m : members) {
            if (!m.equals(uid)) {
                partnerUid = m;
            }
        }
        if (partnerUid != null && !partnerUid.equals(partnerUidShown)) {
            partnerUidShown = partnerUid;
            textPartner.setText(R.string.naming_partner_unknown);
            userRepository.getUser(partnerUid)
                    .addOnSuccessListener(this, doc -> {
                        String nickname = doc.getString(FirestoreSchema.USER_NICKNAME);
                        if (nickname != null) {
                            textPartner.setText(getString(R.string.naming_partner_format, nickname));
                        }
                    });
        }
    }

    private void saveName() {
        clearError();
        String name = text(editGalaxyName).trim();
        String nameError = CoupleInputValidator.validateGalaxyName(name);
        layoutGalaxyName.setError(nameError);
        if (nameError != null || galaxyId == null) {
            return;
        }

        setBusy(true);
        galaxyRepository.nameGalaxy(uid, galaxyId, name)
                .addOnSuccessListener(this, unused -> goMain())
                .addOnFailureListener(this, e -> {
                    setBusy(false);
                    showError(ErrorMessages.toMessage(e));
                });
    }

    private void confirmUnlinkWhileNaming() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.unlink_naming_title)
                .setMessage(R.string.unlink_naming_message)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_unlink, (d, w) -> unlinkWhileNaming())
                .show();
    }

    private void unlinkWhileNaming() {
        if (galaxyId == null) {
            return;
        }
        clearError();
        setBusy(true);
        String gid = galaxyId;
        selfLeaving = true;
        stopWatching(); // 내가 해제한 걸 "상대가 해제"로 표시하지 않도록
        galaxyRepository.unlink(uid, gid)
                .addOnSuccessListener(this, unused -> {
                    selfLeaving = false;
                    setBusy(false);
                    galaxyId = null;
                    partnerUidShown = null;
                    show(Panel.CHOOSE);
                })
                .addOnFailureListener(this, e -> {
                    selfLeaving = false;
                    setBusy(false);
                    showError(ErrorMessages.toMessage(e));
                    watch(gid);
                });
    }

    // ───────────────────────── 공통 UI ─────────────────────────

    private void show(Panel panel) {
        progress.setVisibility(panel == Panel.LOADING || busy ? View.VISIBLE : View.GONE);
        panelChoose.setVisibility(panel == Panel.CHOOSE ? View.VISIBLE : View.GONE);
        panelWaiting.setVisibility(panel == Panel.WAITING ? View.VISIBLE : View.GONE);
        panelNaming.setVisibility(panel == Panel.NAMING ? View.VISIBLE : View.GONE);
    }

    private void setBusy(boolean busy) {
        this.busy = busy;
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        View[] controls = {btnCreate, btnJoin, editCode, btnCopyCode, btnShareCode, btnNewCode,
                btnCancelWaiting, editGalaxyName, btnSaveName, btnUnlinkNaming};
        for (View v : controls) {
            v.setEnabled(!busy);
        }
    }

    private void showError(String message) {
        textError.setText(message);
        textError.setVisibility(View.VISIBLE);
    }

    private void clearError() {
        textError.setVisibility(View.GONE);
        layoutCode.setError(null);
        layoutGalaxyName.setError(null);
    }

    private static String text(TextInputEditText edit) {
        return edit.getText() == null ? "" : edit.getText().toString();
    }


    private void activateAfterLink() {

        if (activationInFlight
                || galaxyId == null
                || uid == null
                || leaving) {
            return;
        }

        activationInFlight = true;

        // 이름을 다시 입력하지 않고 활성화 처리
        show(Panel.LOADING);

        galaxyRepository.activateGalaxy(uid, galaxyId)
                .addOnSuccessListener(this, unused -> {
                    activationInFlight = false;

                    // active 상태 감지 후
                    // 기존 render()가 goMain() 실행
                })
                .addOnFailureListener(this, e -> {

                    activationInFlight = false;

                    show(Panel.NAMING);

                    // 기존 이름 설정 버튼을 재시도용으로 활용
                    btnSaveName.setText("다시 시도");
                    btnSaveName.setOnClickListener(
                            v -> activateAfterLink()
                    );

                    showError(ErrorMessages.toMessage(e));
                });
    }

}

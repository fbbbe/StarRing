package com.example.starring.couple;

import android.app.Activity;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.example.starring.R;
import com.example.starring.common.ErrorMessages;
import com.example.starring.common.Navigator;
import com.example.starring.data.FirestoreSchema;
import com.example.starring.data.FirestoreSchema.GalaxyStatus;
import com.example.starring.data.GalaxyRepository;
import com.example.starring.data.UserRepository;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.firestore.ListenerRegistration;

/**
 * 연동 해제 관련 공용 기능. 메인 화면(팀원 GalaxyActivity 포함)에서 그대로 가져다 쓰면 된다.
 *
 * 사용 예:
 *   // 해제 버튼
 *   UnlinkHelper.confirmAndUnlink(this, uid, galaxyId, () -> watcher.remove());
 *   // onStart / onStop
 *   watcher = UnlinkHelper.watchPartnerUnlink(this, uid, galaxyId);
 *   watcher.remove();
 */
public final class UnlinkHelper {

    private UnlinkHelper() {
    }

    /**
     * 확인 다이얼로그 → 해제 → 커플 연동 화면으로 이동.
     *
     * @param beforeUnlink 해제 직전에 실행 (예: 상대 해제 감지 리스너 제거 — 내가 해제한 걸 "상대가 해제"로 오인하지 않도록)
     */
    public static void confirmAndUnlink(Activity activity, String uid, String galaxyId,
                                        @Nullable Runnable beforeUnlink) {
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.unlink_confirm_title)
                .setMessage(R.string.unlink_confirm_message)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_unlink, (d, w) -> {
                    if (beforeUnlink != null) {
                        beforeUnlink.run();
                    }
                    new GalaxyRepository().unlink(uid, galaxyId)
                            .addOnSuccessListener(activity, unused -> {
                                Toast.makeText(activity, R.string.unlink_done, Toast.LENGTH_SHORT).show();
                                Navigator.toCoupleLink(activity);
                            })
                            .addOnFailureListener(activity, e ->
                                    Toast.makeText(activity, ErrorMessages.toMessage(e),
                                            Toast.LENGTH_LONG).show());
                })
                .show();
    }

    /**
     * 상대가 연동을 해제했는지 실시간 감지. 감지되면 내 galaxyId를 비우고 커플 연동 화면으로 이동.
     * 반환된 리스너는 onStop 등에서 remove() 할 것.
     */
    public static ListenerRegistration watchPartnerUnlink(Activity activity, String uid,
                                                          String galaxyId) {
        return new GalaxyRepository().watchGalaxy(galaxyId, (snap, e) -> {
            if (e != null || snap == null || activity.isFinishing()) {
                return;
            }
            boolean gone = !snap.exists()
                    || GalaxyStatus.UNLINKED.equals(snap.getString(FirestoreSchema.GALAXY_STATUS));
            if (gone) {
                new UserRepository().clearGalaxyId(uid)
                        .addOnCompleteListener(activity, t -> {
                            Toast.makeText(activity, R.string.unlink_by_partner,
                                    Toast.LENGTH_LONG).show();
                            Navigator.toCoupleLink(activity);
                        });
            }
        });
    }
}

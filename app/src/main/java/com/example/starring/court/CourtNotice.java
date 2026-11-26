package com.example.starring.court;

import android.app.Activity;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.example.starring.R;

/**
 * 화면 하단에 잠깐 떴다 사라지는 안내 메시지 (어느 화면에서든 사용 가능).
 *
 *
 * - show(activity, 문구)            : 지금 화면 하단에 바로 표시
 * - setPending(문구) + showPendingIfAny(activity)
 *     : 다른 화면(작성 화면)이 닫힌 뒤, 돌아온 화면의 onResume 에서 대신 표시
 *       (은하계 메인처럼 결과를 받지 않는 화면용)
 */
public final class CourtNotice {

    private static final int BOTTOM_MARGIN_DP = 110;

    private static String pending;

    private CourtNotice() {
    }

    public static void setPending(String message) {
        pending = message;
    }

    /** 예약된 메시지가 있으면 한 번만 표시 */
    public static void showPendingIfAny(Activity activity) {
        if (pending == null) {
            return;
        }
        String message = pending;
        pending = null;
        show(activity, message);
    }

    public static void show(Activity activity, String message) {
        show(activity, message, false);
    }

    /**
     * @param solidBackground true 면 불투명한 검정 배경 (뒤의 행성/별과 겹쳐도 글자가 잘 보임)
     */
    public static void show(Activity activity, String message, boolean solidBackground) {
        final ViewGroup root = activity.findViewById(android.R.id.content);
        if (root == null) {
            return;
        }
        float d = activity.getResources().getDisplayMetrics().density;

        final TextView view = new TextView(activity);
        view.setText(message);
        view.setTextColor(ContextCompat.getColor(activity, R.color.court_text));
        view.setTextSize(15f);
        view.setTypeface(null, Typeface.BOLD);
        view.setGravity(Gravity.CENTER);
        view.setBackgroundResource(solidBackground
                ? R.drawable.court_pill_bg_solid
                : R.drawable.court_pill_bg);
        view.setPadding((int) (20 * d), (int) (12 * d), (int) (20 * d), (int) (12 * d));
        view.setAlpha(0f);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        lp.setMargins((int) (32 * d), 0, (int) (32 * d), (int) (BOTTOM_MARGIN_DP * d));
        root.addView(view, lp);

        view.animate()
                .alpha(1f)
                .setDuration(250)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        view.animate()
                                .alpha(0f)
                                .setStartDelay(1600)
                                .setDuration(400)
                                .withEndAction(new Runnable() {
                                    @Override
                                    public void run() {
                                        root.removeView(view);
                                    }
                                });
                    }
                });
    }
}

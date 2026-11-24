package com.example.starring.court;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.view.View;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.NonNull;

import com.example.starring.R;

/**
 * 은하계 메인 블랙홀 효과
 *
 * - 블랙홀 본체/원반/기류/불씨/파동/후광은 AVD(res/drawable/avd_blackhole.xml)가 그림
 *   (돌아가고 깜빡이는 움직임은 AVD 안에서 알아서 재생됨)
 * - 이 클래스는 AVD를 화면에 놓고, 아래 연출만 담당
 *   · 생성 시 회전하며 커지는 등장 애니메이션
 *   · 블랙홀 아래 타이머 표시 (둥둥 떠다니는 움직임은 뺐음)
 *
 * 모양/색/속도를 바꾸고 싶으면 avd_blackhole.xml, blackhole_vector.xml 을 수정.
 */
public class BlackholeEffect {

    // 등장 애니메이션 시간
    private static final long APPEAR_MS = 1100L;

    // 타이머 깜빡임의 반복 주기
    private static final long SPIN_MS = 24000L;

    // 타이머 깜빡임 횟수 (한 주기 동안)
    private static final int PULSES_PER_SPIN = 10;

    // 타이머 깜빡임 갱신 간격
    private static final long LOOP_INTERVAL_MS = 50L;

    // ★ 블랙홀 크기 조절은 이 숫자 하나만 바꾸면 됨 ★
    // GalaxyView 의 BLACKHOLE_SIZE_DP(600)에 곱해지는 비율.
    // 1.0 이면 정사각형 한 변이 600dp (원반 가로폭 약 480dp)
    // 0.75 → 원반 가로폭 약 360dp / 0.6 → 약 290dp / 0.5 → 약 240dp
    private static final float VECTOR_SCALE = 0.6f;

    // 타이머 글자 크기 (기존 14 → 11). 더 줄이려면 10, 키우려면 12
    private static final float TIMER_TEXT_SP = 11f;

    // 벡터 그림의 실제 내용 높이 비율 (타이머를 블랙홀 바로 아래에 두는 기준)
    private static final float CONTENT_HALF_HEIGHT = 0.4f;

    private final View host;
    private final float density;

    // 블랙홀 AVD (없으면 null)
    private final Drawable drawable;

    private final Paint textPaint =
            new Paint(Paint.ANTI_ALIAS_FLAG);

    private boolean visible = false;
    private boolean running = false;

    // 등장 애니메이션 진행 정도
    private float appear = 0f;

    // 반복 애니메이션 위상
    private float phase = 0f;

    private String label;
    private boolean urgent;

    private ValueAnimator appearAnimator;
    private boolean looping = false;

    // ------------------------------------
    // AVD 가 다시 그려 달라고 할 때 호스트 View 를 갱신
    // (Drawable 은 콜백을 약하게만 잡으므로 필드로 직접 들고 있어야 함)
    // ------------------------------------

    private final Drawable.Callback drawableCallback =
            new Drawable.Callback() {

                @Override
                public void invalidateDrawable(@NonNull Drawable who) {
                    host.invalidate();
                }

                @Override
                public void scheduleDrawable(
                        @NonNull Drawable who,
                        @NonNull Runnable what,
                        long when) {

                    host.postDelayed(
                            what,
                            Math.max(
                                    0L,
                                    when - SystemClock.uptimeMillis()
                            )
                    );
                }

                @Override
                public void unscheduleDrawable(
                        @NonNull Drawable who,
                        @NonNull Runnable what) {

                    host.removeCallbacks(what);
                }
            };

    // ------------------------------------
    // 반복 갱신 (타이머 깜빡임)
    // ------------------------------------

    private final Runnable loopRunnable = new Runnable() {

        @Override
        public void run() {

            if (!looping) {
                return;
            }

            phase = (SystemClock.uptimeMillis() % SPIN_MS)
                    / (float) SPIN_MS;

            host.invalidate();

            host.postDelayed(
                    this,
                    LOOP_INTERVAL_MS
            );
        }
    };

    // ------------------------------------
    // 생성자
    // ------------------------------------

    public BlackholeEffect(View host) {

        this.host = host;

        this.density = host.getResources()
                .getDisplayMetrics().density;

        Drawable d = host.getContext()
                .getDrawable(R.drawable.avd_blackhole);

        if (d != null) {
            d = d.mutate();
            d.setCallback(drawableCallback);
        }

        this.drawable = d;

        // ------------------------------------
        // 타이머 글자 설정
        // ------------------------------------

        textPaint.setTextAlign(
                Paint.Align.CENTER
        );

        textPaint.setTypeface(
                Typeface.create(
                        Typeface.DEFAULT,
                        Typeface.BOLD
                )
        );

        textPaint.setTextSize(
                TIMER_TEXT_SP * density
        );

        // 밝은 별 위에서도 글자가 보이도록 그림자
        textPaint.setShadowLayer(
                3f * density,
                0f,
                0f,
                0xFF000000
        );
    }

    // ------------------------------------
    // 블랙홀 표시 상태
    // ------------------------------------

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean value) {

        if (visible == value) {
            return;
        }

        visible = value;

        if (value) {

            if (running) {

                startAppear();
                startLoop();

            } else {

                appear = 1f;
            }

        } else {

            cancelAnimators();
            appear = 0f;
        }

        host.invalidate();
    }

    // ------------------------------------
    // 블랙홀 아래 타이머
    // ------------------------------------

    public void setLabel(
            String text,
            boolean isUrgent) {

        boolean changed =
                (label == null
                        ? text != null
                        : !label.equals(text))
                        || urgent != isUrgent;

        label = text;
        urgent = isUrgent;

        if (changed && visible) {
            host.invalidate();
        }
    }

    // ------------------------------------
    // 화면 활성화 상태
    // ------------------------------------

    public void setRunning(boolean value) {

        if (running == value) {
            return;
        }

        running = value;

        if (!visible) {
            return;
        }

        if (value) {

            startLoop();

        } else {

            cancelAnimators();
            appear = 1f;
        }
    }

    // ------------------------------------
    // 블랙홀 그리기
    // ------------------------------------

    /**
     * @param canvas   그리기 영역
     * @param cx       블랙홀 중심 X
     * @param cy       블랙홀 중심 Y
     * @param baseHalf 최대 크기의 절반 (줌 적용)
     */
    public void draw(
            Canvas canvas,
            float cx,
            float cy,
            float baseHalf) {

        if (!visible
                || drawable == null
                || appear <= 0.001f) {
            return;
        }

        float a = appear;

        float aClamped = Math.min(
                1f,
                a
        );

        // 타이머 깜빡임 계산
        float pulse =
                0.5f
                        + 0.5f * (float) Math.sin(
                        2 * Math.PI
                                * phase
                                * PULSES_PER_SPIN
                );

        // ------------------------------------
        // 1. 블랙홀 AVD 그리기 (제자리, 흔들림 없음)
        // ------------------------------------

        float bx = cx;
        float by = cy;

        // 등장 중에는 커지면서(a) 회전
        float half = baseHalf * VECTOR_SCALE * a;

        drawable.setAlpha(
                (int) (255 * aClamped)
        );

        drawable.setBounds(
                Math.round(bx - half),
                Math.round(by - half),
                Math.round(bx + half),
                Math.round(by + half)
        );

        drawable.draw(canvas);

        // ------------------------------------
        // 2. 블랙홀 아래 타이머
        // ------------------------------------

        if (label != null && aClamped >= 0.85f) {

            Paint.FontMetrics fm =
                    textPaint.getFontMetrics();

            float contentHalfHeight =
                    baseHalf * VECTOR_SCALE * CONTENT_HALF_HEIGHT;

            float baseline =
                    cy
                            + contentHalfHeight
                            + 8f * density
                            - fm.ascent;

            int color = urgent
                    ? 0xFFFF6B6B
                    : 0xFFFFFFFF;

            float textAlpha = urgent
                    ? (0.55f + 0.45f * pulse)
                    : 1f;

            textPaint.setColor(color);

            textPaint.setAlpha(
                    (int) (255 * textAlpha)
            );

            canvas.drawText(
                    label,
                    cx,
                    baseline,
                    textPaint
            );
        }
    }

    // ------------------------------------
    // 등장 애니메이션
    // ------------------------------------

    private void startAppear() {

        if (appearAnimator != null) {
            appearAnimator.cancel();
        }

        appear = 0f;

        appearAnimator =
                ValueAnimator.ofFloat(0f, 1f);

        appearAnimator.setDuration(
                APPEAR_MS
        );

        appearAnimator.setInterpolator(
                new OvershootInterpolator(1.6f)
        );

        appearAnimator.addUpdateListener(
                animation -> {

                    appear =
                            (float) animation.getAnimatedValue();

                    host.invalidate();
                }
        );

        appearAnimator.start();
    }

    // ------------------------------------
    // 반복 애니메이션 시작 (AVD 재생 포함)
    // ------------------------------------

    private void startLoop() {

        if (looping) {
            return;
        }

        looping = true;

        if (drawable instanceof Animatable) {
            ((Animatable) drawable).start();
        }

        host.post(loopRunnable);
    }

    // ------------------------------------
    // 애니메이션 종료 (AVD 정지 포함)
    // ------------------------------------

    private void cancelAnimators() {

        if (appearAnimator != null) {

            appearAnimator.cancel();
            appearAnimator = null;
        }

        looping = false;

        host.removeCallbacks(
                loopRunnable
        );

        if (drawable instanceof Animatable) {
            ((Animatable) drawable).stop();
        }
    }
}

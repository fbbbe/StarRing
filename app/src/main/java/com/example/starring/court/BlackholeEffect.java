package com.example.starring.court;

import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.View;
import android.view.animation.OvershootInterpolator;

import com.example.starring.R;

/**
 * 은하계 메인에 뜨는 블랙홀의 모양과 움직임 (GalaxyView 가 그릴 때 호출).
 *
 * - 생성될 때: 회전하며 커지고(살짝 튕김) 선명해지는 등장 연출
 * - 평소: 둥둥 떠다님 + 주변 보라-남색 빛(후광)이 맥박처럼 커졌다 작아짐
 * - 블랙홀 아래: 배경 없는 타이머 글자 (1시간 미만이면 붉게 깜빡)
 *
 * 연출 속도/크기를 바꾸고 싶으면 아래 상수만 수정.
 * 계속 도는 부분(회전/맥박)은 초당 약 20번만 갱신해서 기기 부담을 줄임.
 */
public class BlackholeEffect {

    private static final long APPEAR_MS = 1100L;     // 등장 연출 시간
    private static final long SPIN_MS = 24000L;      // 한 바퀴 도는 시간
    private static final int PULSES_PER_SPIN = 10;   // 한 바퀴 동안 후광이 맥박치는 횟수
    private static final float HALO_SCALE = 1.55f;   // 후광 반경 (블랙홀 반지름 대비)
    private static final float HALO_PULSE = 0.12f;   // 후광이 커졌다 작아지는 정도
    private static final long LOOP_INTERVAL_MS = 50L; // 회전/맥박 갱신 간격 (50ms = 초당 20번)

    private final View host;
    private final Bitmap bitmap;
    private final float density;

    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint haloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private boolean visible = false;  // 블랙홀을 보여줄지
    private boolean running = false;  // 화면에 붙어 있고 보이는 중인지 (아닐 때는 애니메이션 정지)
    private float appear = 0f;        // 0 -> 1 (튕기면서 잠깐 1 초과)
    private float phase = 0f;         // 0 -> 1 반복

    private String label;
    private boolean urgent;

    private ValueAnimator appearAnimator;
    private boolean looping = false;

    // 회전/맥박: 시계 시간으로 위상을 계산해 일정한 간격으로 다시 그림
    private final Runnable loopRunnable = new Runnable() {
        @Override
        public void run() {
            if (!looping) {
                return;
            }
            phase = (SystemClock.uptimeMillis() % SPIN_MS) / (float) SPIN_MS;
            host.invalidate();
            host.postDelayed(this, LOOP_INTERVAL_MS);
        }
    };

    public BlackholeEffect(View host) {
        this.host = host;
        this.density = host.getResources().getDisplayMetrics().density;
        this.bitmap = BitmapFactory.decodeResource(host.getResources(), R.drawable.blackhole_aqua);

        // 후광: 반지름 1 짜리 그라데이션을 만들어 두고 크기만 바꿔 그림
        haloPaint.setShader(new RadialGradient(0f, 0f, 1f,
                new int[]{0xAA9B6BFF, 0x553A2E9E, 0x00141052},
                new float[]{0.35f, 0.7f, 1f},
                Shader.TileMode.CLAMP));

        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        textPaint.setTextSize(14f * density);
        // 배경이 없어도 밝은 별 위에서 읽히도록 그림자
        textPaint.setShadowLayer(4f * density, 0f, 0f, 0xFF000000);
    }

    public boolean isVisible() {
        return visible;
    }

    /** 블랙홀 표시 켜기/끄기. 꺼져 있다가 켜지는 순간에만 등장 연출이 나옴 */
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
                appear = 1f; // 화면에 없을 때 켜지면 연출 없이 바로 표시
            }
        } else {
            cancelAnimators();
            appear = 0f;
        }
        host.invalidate();
    }

    /** 블랙홀 아래에 보여줄 글자 (null 이면 숨김), urgent = 붉게 깜빡임 */
    public void setLabel(String text, boolean isUrgent) {
        boolean changed = (label == null ? text != null : !label.equals(text)) || urgent != isUrgent;
        label = text;
        urgent = isUrgent;
        if (changed && visible) {
            host.invalidate();
        }
    }

    /** 화면에 붙어 있고 보일 때만 true (배터리 절약) */
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
            appear = 1f; // 다시 돌아왔을 때 등장 연출 중간 모습으로 멈춰 있지 않게
        }
    }

    /**
     * @param cx, cy   블랙홀 중심 (화면 좌표, px)
     * @param baseHalf 블랙홀 이미지 반지름 (px, 줌 적용)
     */
    public void draw(Canvas canvas, float cx, float cy, float baseHalf) {
        if (!visible || bitmap == null || appear <= 0.001f) {
            return;
        }

        float a = appear;
        float aClamped = Math.min(1f, a);
        float pulse = 0.5f + 0.5f * (float) Math.sin(2 * Math.PI * phase * PULSES_PER_SPIN);

        // 둥둥 떠다니기: 위아래(4초 주기) + 좌우로 살짝(6초 주기)
        // 숫자(6, 4)는 정수로 둘 것 (24초 주기가 이어 붙도록)
        float bobY = baseHalf * 0.08f * (float) Math.sin(2 * Math.PI * phase * 6);
        float bobX = baseHalf * 0.04f * (float) Math.sin(2 * Math.PI * phase * 4 + 1.0);
        float bx = cx + bobX * aClamped;
        float by = cy + bobY * aClamped;

        // 1) 후광 (본체를 따라 같이 떠다님)
        float haloR = baseHalf * (HALO_SCALE + HALO_PULSE * pulse) * a;
        haloPaint.setAlpha((int) (255 * (0.6f + 0.4f * pulse) * aClamped));
        canvas.save();
        canvas.translate(bx, by);
        canvas.scale(haloR, haloR);
        canvas.drawCircle(0f, 0f, 1f, haloPaint);
        canvas.restore();

        // 2) 본체: 평소엔 회전 없이 떠다니고, 등장할 때만 돌면서 커짐
        float half = baseHalf * a;
        float angle = (1f - aClamped) * 270f;
        bitmapPaint.setAlpha((int) (255 * aClamped));
        canvas.save();
        canvas.rotate(angle, bx, by);
        rect.set(bx - half, by - half, bx + half, by + half);
        canvas.drawBitmap(bitmap, null, rect, bitmapPaint);
        canvas.restore();

        // 3) 아래 타이머: 배경 없이 글자만 (위치는 고정)
        if (label != null && aClamped >= 0.85f) {
            Paint.FontMetrics fm = textPaint.getFontMetrics();
            float baseline = cy + baseHalf + 12f * density - fm.ascent;

            int color = urgent ? 0xFFFF6B6B : 0xFFFFFFFF;
            float textAlpha = urgent ? (0.55f + 0.45f * pulse) : 1f;

            textPaint.setColor(color);
            textPaint.setAlpha((int) (255 * textAlpha));
            canvas.drawText(label, cx, baseline, textPaint);
        }
    }

    // ----- 내부 -----

    private void startAppear() {
        if (appearAnimator != null) {
            appearAnimator.cancel();
        }
        appear = 0f;
        appearAnimator = ValueAnimator.ofFloat(0f, 1f);
        appearAnimator.setDuration(APPEAR_MS);
        appearAnimator.setInterpolator(new OvershootInterpolator(1.6f));
        appearAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                appear = (float) animation.getAnimatedValue();
                host.invalidate();
            }
        });
        appearAnimator.start();
    }

    private void startLoop() {
        if (looping) {
            return;
        }
        looping = true;
        host.post(loopRunnable);
    }

    private void cancelAnimators() {
        if (appearAnimator != null) {
            appearAnimator.cancel();
            appearAnimator = null;
        }
        looping = false;
        host.removeCallbacks(loopRunnable);
    }
}

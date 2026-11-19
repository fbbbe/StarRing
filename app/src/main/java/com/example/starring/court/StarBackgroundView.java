package com.example.starring.court;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import java.util.Random;

/**
 * 우주 재판장 공용 배경 (그라데이션 + 성운 + 반짝이는 별).
 * 재판장 메인 / 작성 / 투표 화면 레이아웃 맨 뒤에 깔아서 사용.
 * 별 개수, 색, 반짝임 속도를 바꾸고 싶으면 이 파일만 수정하면 됨.
 */
public class StarBackgroundView extends View {

    private static final int STAR_COUNT = 70;
    private static final long CYCLE_MS = 6000L; // 반짝임 한 주기

    private final Paint bgPaint = new Paint();
    private final Paint nebulaPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint starPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // 별 정보 (좌표는 화면 대비 0~1 비율)
    private final float[] starX = new float[STAR_COUNT];
    private final float[] starY = new float[STAR_COUNT];
    private final float[] starRadiusDp = new float[STAR_COUNT];
    private final float[] starPhase = new float[STAR_COUNT];
    private final int[] starBlinks = new int[STAR_COUNT]; // 한 주기 동안 깜빡이는 횟수

    private float density;
    private float progress; // 0~1 반복
    private ValueAnimator animator;

    public StarBackgroundView(Context context) {
        super(context);
        init();
    }

    public StarBackgroundView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public StarBackgroundView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        density = getResources().getDisplayMetrics().density;

        // 고정 시드: 화면을 열 때마다 같은 별자리 배치
        Random random = new Random(7);
        for (int i = 0; i < STAR_COUNT; i++) {
            starX[i] = random.nextFloat();
            starY[i] = random.nextFloat();
            starRadiusDp[i] = 0.35f + random.nextFloat() * 0.55f;
            starPhase[i] = random.nextFloat();
            starBlinks[i] = 1 + random.nextInt(3); // 1~3 (정수여야 반복이 끊기지 않음)
        }
        starPaint.setColor(0xFFFFFFFF);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        bgPaint.setShader(new LinearGradient(
                0, 0, 0, h,
                new int[]{0xFF0A0818, 0xFF17123A, 0xFF100D24},
                null,
                Shader.TileMode.CLAMP));

        // 우측 상단에 은은한 보라빛 성운
        nebulaPaint.setShader(new RadialGradient(
                w * 0.85f, h * 0.15f, Math.max(w, 1) * 0.9f,
                0x446C4FD6, 0x006C4FD6,
                Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();

        canvas.drawRect(0, 0, w, h, bgPaint);
        canvas.drawRect(0, 0, w, h, nebulaPaint);

        for (int i = 0; i < STAR_COUNT; i++) {
            double angle = 2 * Math.PI * (progress * starBlinks[i] + starPhase[i]);
            float wave = 0.5f + 0.5f * (float) Math.sin(angle); // 0~1
            starPaint.setAlpha((int) (255 * (0.25f + 0.75f * wave)));
            canvas.drawCircle(starX[i] * w, starY[i] * h, starRadiusDp[i] * density, starPaint);
        }
    }

    // 화면에 보일 때만 애니메이션 동작 (배터리 절약)
    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(CYCLE_MS);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                progress = (float) animation.getAnimatedValue();
                invalidate();
            }
        });
        animator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        super.onDetachedFromWindow();
    }
}

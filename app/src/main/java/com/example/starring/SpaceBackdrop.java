package com.example.starring;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.drawable.ColorDrawable;
import android.os.SystemClock;
import android.view.View;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import android.view.animation.LinearInterpolator;

import java.util.Random;

/**
 * 연한 우주 배경: 그라데이션 + 성운 빛 + 반짝이는 별 + 별똥별.
 * (우주 재판장 StarBackgroundView 와 같은 방식, 연한 색으로)
 * 행성 생성 / 행성 상세 화면이 함께 사용한다.
 * 별 개수, 색, 반짝임 속도, 별똥별은 아래 상수만 바꾸면 된다.
 */
class SpaceBackdrop extends View {

    private static final int STAR_COUNT = 70;
    private static final long CYCLE_MS = 6000L;   // 반짝임 한 주기

    // 별똥별 (우주 재판장과 같은 크기/속도)
    private static final long METEOR_DURATION_MS = 1800L;
    private static final long METEOR_FIRST_MS = 2500L;
    private static final long METEOR_GAP_MS = 2000L;

    // 연한 배경 위에서 보이는 별 색
    private static final int[] STAR_COLORS = {
            0xFF9C8CF0,   // 연보라
            0xFFB9A8F5,   // 더 연한 보라
            0xFFF2B8D8,   // 연분홍
            0xFFF3C969,   // 연한 금색
            0xFFA9C4FF    // 연한 하늘색
    };

    private static final int METEOR_COLOR = 0xFFFFFFFF;   // 흰색

    private final Paint bgPaint = new Paint();
    private final Paint glowPaint1 = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint2 = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint3 = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint starPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint meteorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path sparklePath = new Path();

    private final float[] starX = new float[STAR_COUNT];
    private final float[] starY = new float[STAR_COUNT];
    private final float[] starRadius = new float[STAR_COUNT];
    private final float[] starPhase = new float[STAR_COUNT];
    private final int[] starBlinks = new int[STAR_COUNT];
    private final int[] starColor = new int[STAR_COUNT];
    private final boolean[] starSparkle = new boolean[STAR_COUNT];

    private final Random meteorRandom = new Random();
    private long meteorStartAt;
    private long nextMeteorAt;
    private boolean meteorActive;
    private float mx, my, mdx, mdy, mTravel, mTail;

    private final float density;
    private float progress;
    private ValueAnimator animator;

    private float g1x, g1y, g1r, g2x, g2y, g2r, g3x, g3y, g3r;

    SpaceBackdrop(Context context) {
        super(context);

        density = context.getResources().getDisplayMetrics().density;

        // 고정 시드: 화면을 열 때마다 같은 별 배치
        Random random = new Random(20240601L);

        for (int i = 0; i < STAR_COUNT; i++) {

            starX[i] = random.nextFloat();
            starY[i] = random.nextFloat();
            starPhase[i] = random.nextFloat();
            starBlinks[i] = 1 + random.nextInt(3);   // 정수여야 반복이 끊기지 않음
            starColor[i] = STAR_COLORS[random.nextInt(STAR_COLORS.length)];

            starSparkle[i] = random.nextInt(9) == 0;

            starRadius[i] = starSparkle[i]
                    ? 3.6f + random.nextFloat() * 2.4f
                    : 0.8f + random.nextFloat() * 1.4f;
        }

        meteorPaint.setColor(METEOR_COLOR);
        meteorPaint.setStrokeCap(Paint.Cap.ROUND);
        meteorPaint.setStrokeWidth(2f * density);

        nextMeteorAt = SystemClock.uptimeMillis() + METEOR_FIRST_MS;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        // 위: 연한 하늘빛 보라 -> 아래: 연한 분홍빛
        bgPaint.setShader(new LinearGradient(
                0f, 0f, 0f, h,
                new int[]{0xFFE9E4FF, 0xFFF4EEFF, 0xFFFBEFF7},
                new float[]{0f, 0.55f, 1f},
                Shader.TileMode.CLAMP
        ));

        // 성운처럼 번지는 은은한 빛 3곳 (미리 만들어 두고 매 프레임 재사용)
        g1x = w * 0.15f;
        g1y = h * 0.12f;
        g1r = w * 0.85f;
        glowPaint1.setShader(radial(g1x, g1y, g1r, 0x55B3A6FF));

        g2x = w * 0.95f;
        g2y = h * 0.50f;
        g2r = w * 0.75f;
        glowPaint2.setShader(radial(g2x, g2y, g2r, 0x44F4B6DC));

        g3x = w * 0.10f;
        g3y = h * 0.88f;
        g3r = w * 0.80f;
        glowPaint3.setShader(radial(g3x, g3y, g3r, 0x449FC3FF));
    }

    private Shader radial(float x, float y, float r, int color) {
        return new RadialGradient(
                x, y, Math.max(r, 1f),
                color, color & 0x00FFFFFF,
                Shader.TileMode.CLAMP
        );
    }

    @Override
    protected void onDraw(Canvas canvas) {

        int w = getWidth();
        int h = getHeight();

        canvas.drawRect(0f, 0f, w, h, bgPaint);

        canvas.drawCircle(g1x, g1y, g1r, glowPaint1);
        canvas.drawCircle(g2x, g2y, g2r, glowPaint2);
        canvas.drawCircle(g3x, g3y, g3r, glowPaint3);

        // 반짝이는 별
        for (int i = 0; i < STAR_COUNT; i++) {

            double angle = 2.0 * Math.PI
                    * (progress * starBlinks[i] + starPhase[i]);

            float wave = 0.5f + 0.5f * (float) Math.sin(angle);

            int alpha = (int) (255 * (0.25f + 0.75f * wave) * 0.8f);

            starPaint.setColor(
                    (alpha << 24) | (starColor[i] & 0x00FFFFFF)
            );

            float x = starX[i] * w;
            float y = starY[i] * h;
            float r = starRadius[i] * density;

            if (starSparkle[i]) {
                drawSparkle(canvas, x, y, r);
            } else {
                canvas.drawCircle(x, y, r, starPaint);
            }
        }

        drawMeteor(canvas, w, h);
    }

    // ----- 네 갈래로 반짝이는 별 (✦) -----

    private void drawSparkle(Canvas canvas, float x, float y, float r) {

        float k = r * 0.22f;

        sparklePath.reset();
        sparklePath.moveTo(x, y - r);
        sparklePath.quadTo(x + k, y - k, x + r, y);
        sparklePath.quadTo(x + k, y + k, x, y + r);
        sparklePath.quadTo(x - k, y + k, x - r, y);
        sparklePath.quadTo(x - k, y - k, x, y - r);
        sparklePath.close();

        canvas.drawPath(sparklePath, starPaint);
    }

    // ----- 별똥별: 가끔 한 번씩 오른쪽 위에서 왼쪽 아래로 -----

    private void drawMeteor(Canvas canvas, int w, int h) {

        long now = SystemClock.uptimeMillis();

        if (!meteorActive) {

            if (now < nextMeteorAt) return;

            meteorActive = true;
            meteorStartAt = now;

            mx = w * (0.35f + meteorRandom.nextFloat() * 0.65f);
            my = h * (0.02f + meteorRandom.nextFloat() * 0.35f);

            double angle = Math.toRadians(25 + meteorRandom.nextInt(20));

            mdx = (float) -Math.cos(angle);
            mdy = (float) Math.sin(angle);

            mTravel = w * (0.35f + meteorRandom.nextFloat() * 0.25f);
            mTail = (100 + meteorRandom.nextInt(50)) * density;
        }

        float t = (now - meteorStartAt) / (float) METEOR_DURATION_MS;

        if (t >= 1f) {
            meteorActive = false;
            nextMeteorAt = now + METEOR_GAP_MS;
            return;
        }

        float envelope = (float) Math.sin(Math.PI * t);

        float hx = mx + mdx * mTravel * t;
        float hy = my + mdy * mTravel * t;

        int segments = 12;

        for (int i = 0; i < segments; i++) {

            float f0 = i / (float) segments;
            float f1 = (i + 1) / (float) segments;

            float alpha = (1f - f0) * 0.9f * envelope;

            meteorPaint.setAlpha((int) (255 * alpha));

            canvas.drawLine(
                    hx - mdx * mTail * f0, hy - mdy * mTail * f0,
                    hx - mdx * mTail * f1, hy - mdy * mTail * f1,
                    meteorPaint
            );
        }

        meteorPaint.setAlpha((int) (255 * envelope));
        canvas.drawCircle(hx, hy, 2.4f * density, meteorPaint);
    }

    // 화면에 보일 때만 애니메이션 동작 (배터리 절약)
    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(CYCLE_MS);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());

        animator.addUpdateListener(animation -> {
            progress = (float) animation.getAnimatedValue();
            invalidate();
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

    // ------------------------------------
    // 화면 창을 연한 색으로 (검은 배경이 비치지 않게)
    // ------------------------------------

    /**
     * 창 배경을 연한 라벤더로 바꾸고, 상태바/내비게이션 바 글자를
     * 어두운 색으로 바꿈. 화면이 넘어가는 중이나 사진 선택기에서
     * 돌아올 때 검은 바탕이 비치는 것을 막는다.
     */
    static void applyLightWindow(Activity activity) {

        activity.getWindow().setBackgroundDrawable(
                new ColorDrawable(Color.parseColor("#EDE6FF"))
        );

        // WindowCompat: 안드로이드 버전에 상관없이 안전하게 상태바 글자색 변경
        // (decorView 를 먼저 만들어 주므로 setContentView 전에 불러도 안 튕김)
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(
                        activity.getWindow(),
                        activity.getWindow().getDecorView()
                );

        controller.setAppearanceLightStatusBars(true);
        controller.setAppearanceLightNavigationBars(true);
    }
}

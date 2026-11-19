package com.example.starring.common;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Random;

/**
 * 인증/연동 화면 배경용 반짝이는 별 View (Canvas 2D).
 * 장식용이라 고정 시드를 사용 → 화면을 다시 열어도 같은 자리에 별이 그려진다.
 * (은하계 메인 화면의 실제 별 계산과는 무관)
 */
public class StarFieldView extends View {

    private static final long SEED = 20261119L;
    /** 화면 면적(px²)당 별 개수 */
    private static final float DENSITY = 1f / 9000f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float[] xs = new float[0];
    private float[] ys = new float[0];
    private float[] radii = new float[0];
    private float[] baseAlpha = new float[0];
    private float[] phase = new float[0];
    private float[] speed = new float[0];
    private int[] colors = new int[0];

    private static final int[] PALETTE = {
            0xFFFFFFFF, 0xFFFFF4D6, 0xFFD6E4FF, 0xFFFFD97A
    };

    public StarFieldView(Context context) {
        super(context);
    }

    public StarFieldView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        generateStars(w, h);
    }

    private void generateStars(int w, int h) {
        int count = Math.max(40, (int) (w * h * DENSITY));
        float dp = getResources().getDisplayMetrics().density;
        Random random = new Random(SEED);

        xs = new float[count];
        ys = new float[count];
        radii = new float[count];
        baseAlpha = new float[count];
        phase = new float[count];
        speed = new float[count];
        colors = new int[count];

        for (int i = 0; i < count; i++) {
            xs[i] = random.nextFloat() * w;
            ys[i] = random.nextFloat() * h;
            // 대부분 작은 별, 가끔 큰 별
            float size = random.nextFloat();
            radii[i] = (size > 0.95f ? 1.8f : size > 0.75f ? 1.2f : 0.7f) * dp;
            baseAlpha[i] = 0.35f + random.nextFloat() * 0.65f;
            phase[i] = random.nextFloat() * (float) (Math.PI * 2);
            speed[i] = 0.6f + random.nextFloat() * 1.6f;
            colors[i] = PALETTE[random.nextInt(PALETTE.length)];
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        // 애니메이션 진행용 시간 (저장/계산 기준값이 아님)
        float t = SystemClock.uptimeMillis() / 1000f;

        for (int i = 0; i < xs.length; i++) {
            float twinkle = 0.6f + 0.4f * (float) Math.sin(t * speed[i] + phase[i]);
            paint.setColor(colors[i]);
            paint.setAlpha((int) (255 * baseAlpha[i] * twinkle));
            canvas.drawCircle(xs[i], ys[i], radii[i], paint);
        }

        if (isShown()) {
            postInvalidateOnAnimation();
        }
    }

    @Override
    protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == VISIBLE) {
            postInvalidateOnAnimation();
        }
    }
}

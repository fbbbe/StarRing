
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
 * 은하계 메인 블랙홀 효과
 *
 * - 원본 PNG의 가로세로 비율 유지
 * - 생성 시 회전하며 커지는 등장 애니메이션
 * - 평소 둥둥 떠다니는 움직임
 * - 은은한 다홍색 후광 및 맥박 효과
 * - 블랙홀 아래 타이머 표시
 */
public class BlackholeEffect {

    // 등장 애니메이션 시간
    private static final long APPEAR_MS = 1100L;

    // 애니메이션 전체 반복 주기
    private static final long SPIN_MS = 24000L;

    // 한 주기 동안 후광이 맥박치는 횟수
    private static final int PULSES_PER_SPIN = 10;

    // 블랙홀 크기 대비 후광 반경
    private static final float HALO_SCALE = 1.55f;

    // 후광 크기 변화 정도
    private static final float HALO_PULSE = 0.12f;

    // 애니메이션 갱신 간격
    private static final long LOOP_INTERVAL_MS = 50L;

    private final View host;
    private final Bitmap bitmap;
    private final float density;

    // 원본 이미지의 가로세로 크기
    private final float imageWidth;
    private final float imageHeight;

    // 원본 이미지의 긴 쪽을 기준으로 계산한 비율
    private final float widthRatio;
    private final float heightRatio;

    private final Paint bitmapPaint =
            new Paint(
                    Paint.ANTI_ALIAS_FLAG
                            | Paint.FILTER_BITMAP_FLAG
            );

    private final Paint haloPaint =
            new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Paint textPaint =
            new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF rect = new RectF();

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
    // 반복 애니메이션
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

        this.bitmap = BitmapFactory.decodeResource(
                host.getResources(),
                R.drawable.blackhole_aqua
        );

        // 원본 PNG의 비율 계산
        if (bitmap != null) {

            imageWidth = bitmap.getWidth();
            imageHeight = bitmap.getHeight();

            // 긴 쪽을 1로 설정
            float maxDimension = Math.max(
                    imageWidth,
                    imageHeight
            );

            widthRatio = imageWidth / maxDimension;
            heightRatio = imageHeight / maxDimension;

        } else {

            imageWidth = 1f;
            imageHeight = 1f;

            widthRatio = 1f;
            heightRatio = 1f;
        }

        // ------------------------------------
        // 다홍색 후광 설정
        // ------------------------------------

        haloPaint.setShader(
                new RadialGradient(
                        0f,
                        0f,
                        1f,

                        new int[]{
                                0x88FF8864, // 안쪽: 밝은 다홍색
                                0x44ED5A44, // 중간: 부드러운 코랄 레드
                                0x00D94939  // 바깥: 투명하게 사라짐
                        },

                        new float[]{
                                0.25f,
                                0.65f,
                                1f
                        },

                        Shader.TileMode.CLAMP
                )
        );

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
                14f * density
        );

        // 밝은 별 위에서도 글자가 보이도록 그림자
        textPaint.setShadowLayer(
                4f * density,
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
                || bitmap == null
                || appear <= 0.001f) {
            return;
        }

        float a = appear;

        float aClamped = Math.min(
                1f,
                a
        );

        // 후광 맥박 계산
        float pulse =
                0.5f
                        + 0.5f * (float) Math.sin(
                        2 * Math.PI
                                * phase
                                * PULSES_PER_SPIN
                );

        // ------------------------------------
        // 1. 둥둥 떠다니는 움직임
        // ------------------------------------

        float bobY =
                baseHalf * 0.08f
                        * (float) Math.sin(
                        2 * Math.PI * phase * 6
                );

        float bobX =
                baseHalf * 0.04f
                        * (float) Math.sin(
                        2 * Math.PI * phase * 4 + 1.0
                );

        float bx = cx + bobX * aClamped;
        float by = cy + bobY * aClamped;

        // ------------------------------------
        // 2. 다홍색 후광 그리기
        // ------------------------------------

        float haloR =
                baseHalf
                        * (HALO_SCALE + HALO_PULSE * pulse)
                        * a;

        haloPaint.setAlpha(
                (int) (
                        255
                                * (0.6f + 0.4f * pulse)
                                * aClamped
                )
        );

        canvas.save();

        canvas.translate(bx, by);

        canvas.scale(
                haloR,
                haloR
        );

        canvas.drawCircle(
                0f,
                0f,
                1f,
                haloPaint
        );

        canvas.restore();

        // ------------------------------------
        // 3. 원본 비율을 유지한 블랙홀 이미지
        // ------------------------------------

        // 긴 쪽을 기준으로 가로와 세로 크기 계산
        float halfWidth =
                baseHalf * widthRatio * a;

        float halfHeight =
                baseHalf * heightRatio * a;

        // 등장 애니메이션 회전 각도
        float angle =
                (1f - aClamped) * 270f;

        bitmapPaint.setAlpha(
                (int) (255 * aClamped)
        );

        canvas.save();

        canvas.rotate(
                angle,
                bx,
                by
        );

        // 정사각형으로 강제하지 않고
        // 원본 가로세로 비율에 맞춰 그리기
        rect.set(
                bx - halfWidth,
                by - halfHeight,
                bx + halfWidth,
                by + halfHeight
        );

        canvas.drawBitmap(
                bitmap,
                null,
                rect,
                bitmapPaint
        );

        canvas.restore();

        // ------------------------------------
        // 4. 블랙홀 아래 타이머
        // ------------------------------------

        if (label != null && aClamped >= 0.85f) {

            Paint.FontMetrics fm =
                    textPaint.getFontMetrics();

            // 실제 이미지 높이를 기준으로 배치
            // 기존 정사각형 기준의 과도한 간격 방지
            float imageHalfHeight =
                    baseHalf * heightRatio;

            float baseline =
                    cy
                            + imageHalfHeight
                            + 12f * density
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
    // 반복 애니메이션 시작
    // ------------------------------------

    private void startLoop() {

        if (looping) {
            return;
        }

        looping = true;

        host.post(loopRunnable);
    }

    // ------------------------------------
    // 애니메이션 종료
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
    }
}

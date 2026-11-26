package com.example.starring.court;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.View;
import android.view.animation.OvershootInterpolator;

import java.util.Random;

import androidx.annotation.NonNull;

import com.example.starring.R;

/**
 * 은하계 메인 블랙홀 효과
 *
 * - 블랙홀 본체/원반/기류/불씨/파동/후광은 AVD(res/drawable/avd_blackhole.xml)가 그림
 *   (돌아가고 깜빡이는 움직임은 AVD 안에서 알아서 재생됨)
 * - 이 클래스는 AVD를 화면에 놓고, 아래 연출만 담당
 *   · 생성 시 작았다가 커지는 등장 애니메이션 (회전 없음)
 *   · 주변 별이 천천히 블랙홀로 빨려 들어가는 연출
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

    // ------------------------------------
    // 빨려 들어가는 별 (숫자만 바꿔서 느낌 조절)
    // ------------------------------------

    // 별 개수 (많을수록 북적임. 20~40 추천)
    private static final int STAR_COUNT = 28;

    // 별이 시작하는 거리 = 블랙홀 그림 반지름의 몇 배 (클수록 넓은 범위)
    private static final float STAR_RANGE = 1.9f;

    // 가장 밝은 별의 투명도 (0~1, 작을수록 보일락말락)
    private static final float STAR_MAX_ALPHA = 0.55f;

    // 한 개의 별이 바깥에서 블랙홀까지 가는 시간 범위 (ms, 길수록 천천히)
    private static final int STAR_MIN_PERIOD_MS = 7000;
    private static final int STAR_MAX_PERIOD_MS = 14000;

    // 세로로 납작하게 (1.0 = 완전한 원, 작을수록 납작. 원반 기울기와 어울리게)
    private static final float STAR_FLATTEN = 0.7f;

    // 별 크기 범위 (dp)
    private static final float STAR_MIN_DP = 0.8f;
    private static final float STAR_MAX_DP = 2.2f;

    // 타이머 글자 크기 (기존 14 → 11). 더 줄이려면 10, 키우려면 12
    private static final float TIMER_TEXT_SP = 11f;

    // ------------------------------------
    // 가끔 화면이 흔들리고 화해 메시지가 뜨는 연출 (드물게)
    // ------------------------------------

    // 블랙홀이 나타난(또는 화면에 들어온) 뒤 첫 흔들림까지 기다리는 시간 (ms)
    private static final long SHAKE_FIRST_MIN_MS = 25000L;
    private static final long SHAKE_FIRST_MAX_MS = 45000L;

    // 흔들림과 흔들림 사이 간격 (ms): 1분 10초 ~ 2분 10초
    private static final long SHAKE_GAP_MIN_MS = 70000L;
    private static final long SHAKE_GAP_MAX_MS = 130000L;

    // 흔들리는 시간 (ms)과 세기 (dp, 점점 약해짐)
    private static final long SHAKE_MS = 900L;
    private static final float SHAKE_AMP_DP = 3.5f;

    // 흔들릴 때마다 하나씩 돌아가며 나오는 문구
    // (화면 하단 중앙의 안내 메시지 CourtNotice 로 표시됨)
    private static final String[] PEACE_MESSAGES = {
            "블랙홀이 흔들려요… 화해해 볼까요?",
            "곧 판결이 내려져요. 먼저 손 내밀어요",
            "서로의 마음을 먼저 들어줄까요?",
            "아직 늦지 않았어요. 화해해요"
    };

    // 벡터 그림의 실제 내용 높이 비율 (타이머를 블랙홀 바로 아래에 두는 기준)
    private static final float CONTENT_HALF_HEIGHT = 0.4f;

    private final View host;
    private final float density;

    // 블랙홀 AVD (없으면 null)
    private final Drawable drawable;

    private final Paint textPaint =
            new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Paint starPaint =
            new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Random shakeRandom = new Random();

    // 다음 흔들림 시각, 지금 흔들림이 시작된 시각 (uptimeMillis, 없으면 -1)
    private long nextShakeAt = -1L;
    private long shakeStartAt = -1L;
    private int messageIndex = 0;

    // 별마다 다른 값 (생성할 때 한 번만 정하므로 언제 봐도 같은 모양)
    private final float[] starAngle = new float[STAR_COUNT];
    private final float[] starSpin = new float[STAR_COUNT];
    private final float[] starOffset = new float[STAR_COUNT];
    private final float[] starSize = new float[STAR_COUNT];
    private final float[] starAlpha = new float[STAR_COUNT];
    private final int[] starPeriod = new int[STAR_COUNT];
    private final int[] starColor = new int[STAR_COUNT];

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

            updateShake(SystemClock.uptimeMillis());

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
        // 별 값 정하기 (시드 고정 → 항상 같은 모양)
        // ------------------------------------

        int[] palette = {
                0xFFFFFFFF, // 흰색
                0xFFFFE6CC, // 따뜻한 흰색
                0xFFCCDDFF, // 푸른 흰색
                0xFFFFD0B0  // 연한 주황
        };

        Random random = new Random(20241124L);

        for (int i = 0; i < STAR_COUNT; i++) {

            starAngle[i] = random.nextFloat() * 6.2831855f;

            // 안쪽으로 오면서 감기는 각도 (시계 방향)
            starSpin[i] = 0.7f + random.nextFloat() * 0.9f;

            starOffset[i] = random.nextFloat();

            starSize[i] = (STAR_MIN_DP
                    + random.nextFloat()
                    * (STAR_MAX_DP - STAR_MIN_DP)) * density;

            starAlpha[i] = 0.25f
                    + random.nextFloat() * (STAR_MAX_ALPHA - 0.25f);

            starPeriod[i] = STAR_MIN_PERIOD_MS
                    + random.nextInt(
                    STAR_MAX_PERIOD_MS - STAR_MIN_PERIOD_MS + 1
            );

            starColor[i] = palette[random.nextInt(palette.length)];
        }

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

        // 별은 블랙홀 뒤에 그려서 본체에 가려지며 빨려 들어가는 느낌
        drawStars(canvas, bx, by, baseHalf, aClamped);

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
    // 가끔 흔들림: 시각 관리 / 흔들림 크기
    // ------------------------------------

    private long randomBetween(long min, long max) {
        return min + (long) (shakeRandom.nextDouble() * (max - min));
    }

    /** 흔들릴 때가 됐는지 확인 (반복 갱신에서 호출) */
    private void updateShake(long now) {

        if (!visible || nextShakeAt < 0L || now < nextShakeAt) {
            return;
        }

        // 메시지는 항상 보여주고, 흔들림은 시스템 "애니메이션 끄기" 설정을 존중
        showPeaceMessage();

        shakeStartAt = animationsEnabled() ? now : -1L;

        nextShakeAt = now + randomBetween(SHAKE_GAP_MIN_MS, SHAKE_GAP_MAX_MS);
    }

    /** 화면 하단 중앙에 화해 메시지를 띄움 (블랙홀 생성 안내와 같은 모양) */
    private void showPeaceMessage() {

        Activity activity = findActivity(host.getContext());

        if (activity == null
                || activity.isFinishing()
                || activity.isDestroyed()) {
            return;
        }

        String message =
                PEACE_MESSAGES[messageIndex % PEACE_MESSAGES.length];

        messageIndex++;

        CourtNotice.show(activity, message, true);
    }

    private static Activity findActivity(Context context) {

        while (context instanceof ContextWrapper) {

            if (context instanceof Activity) {
                return (Activity) context;
            }

            context = ((ContextWrapper) context).getBaseContext();
        }

        return null;
    }

    private boolean animationsEnabled() {

        try {
            return Settings.Global.getFloat(
                    host.getContext().getContentResolver(),
                    Settings.Global.ANIMATOR_DURATION_SCALE,
                    1f
            ) > 0f;
        } catch (RuntimeException e) {
            return true;
        }
    }

    /** 지금 화면이 흔들리는 중인지 (흔들리는 동안만 부드럽게 다시 그리기 위해) */
    public boolean isShaking() {

        return visible
                && shakeStartAt >= 0L
                && SystemClock.uptimeMillis() - shakeStartAt <= SHAKE_MS;
    }

    /** 화면 전체에 더할 가로 흔들림(px). 흔들리지 않을 때는 0 */
    public float shakeX() {
        return shakeOffset(true);
    }

    /** 화면 전체에 더할 세로 흔들림(px). 흔들리지 않을 때는 0 */
    public float shakeY() {
        return shakeOffset(false);
    }

    private float shakeOffset(boolean horizontal) {

        if (!isShaking()) {
            return 0f;
        }

        long elapsed = SystemClock.uptimeMillis() - shakeStartAt;

        // 점점 약해짐
        float decay = 1f - elapsed / (float) SHAKE_MS;
        float amp = SHAKE_AMP_DP * density * decay;

        double t = elapsed / 1000.0;

        return horizontal
                ? (float) (Math.sin(2.0 * Math.PI * 17.0 * t) * amp)
                : (float) (Math.cos(2.0 * Math.PI * 23.0 * t) * amp * 0.6f);
    }

    // ------------------------------------
    // 블랙홀로 빨려 들어가는 별
    // ------------------------------------

    /**
     * 각 별은 "바깥 → 블랙홀"을 한 번 가는 동안
     * 천천히 나타나고, 점점 빨라지며 작아지고, 가까워지면 사라짐.
     * 시계 시간으로 위치를 계산하므로 따로 상태를 저장하지 않음.
     */
    private void drawStars(
            Canvas canvas,
            float cx,
            float cy,
            float baseHalf,
            float appearAlpha) {

        long now = SystemClock.uptimeMillis();

        float outer = baseHalf * VECTOR_SCALE * STAR_RANGE;
        float inner = baseHalf * VECTOR_SCALE * 0.28f;

        for (int i = 0; i < STAR_COUNT; i++) {

            // 0 (바깥) → 1 (블랙홀)
            float t = ((now % starPeriod[i])
                    / (float) starPeriod[i]
                    + starOffset[i]) % 1f;

            // 처음엔 천천히, 가까워질수록 빨라짐
            float ease = t * t;

            float radius = outer + (inner - outer) * ease;

            float angle = starAngle[i] + starSpin[i] * t;

            float x = cx + radius * (float) Math.cos(angle);
            float y = cy + radius * (float) Math.sin(angle)
                    * STAR_FLATTEN;

            // 나타날 때 서서히, 사라질 때 서서히
            float fadeIn = Math.min(1f, t / 0.25f);
            float fadeOut = Math.min(1f, (1f - t) / 0.3f);

            float alpha = starAlpha[i]
                    * fadeIn * fadeOut * appearAlpha;

            if (alpha <= 0.01f) {
                continue;
            }

            // 가까워질수록 작아짐
            float size = starSize[i] * (1f - 0.5f * t);

            starPaint.setColor(starColor[i]);

            // 큰 별은 은은한 빛무리를 한 겹 더
            if (starSize[i] > 1.6f * density) {

                starPaint.setAlpha((int) (255 * alpha * 0.25f));
                canvas.drawCircle(x, y, size * 2.4f, starPaint);
            }

            starPaint.setAlpha((int) (255 * alpha));
            canvas.drawCircle(x, y, size, starPaint);
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

        // 보이자마자 흔들리지 않도록, 처음엔 한참 있다가 첫 흔들림
        nextShakeAt = SystemClock.uptimeMillis()
                + randomBetween(SHAKE_FIRST_MIN_MS, SHAKE_FIRST_MAX_MS);

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

        // 흔들림/메시지 정리
        nextShakeAt = -1L;
        shakeStartAt = -1L;

        host.removeCallbacks(
                loopRunnable
        );

        if (drawable instanceof Animatable) {
            ((Animatable) drawable).stop();
        }
    }
}

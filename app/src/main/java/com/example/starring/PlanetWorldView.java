package com.example.starring;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * 행성 상세의 "행성" 화면.
 *
 * - 아기자기하게 꾸민 행성이 가운데에 떠 있음 (고리, 꽃, 버섯, 나무 등)
 * - 우주 재판장 눈싸움 외계인과 같은 모습의 외계인 두 명이 행성 위를 걸어다님
 * - 사진이 있는 날짜마다 핀이 꽂히고, 핀을 누르면 listener 로 알려줌
 *   (행성 하나 = 한 달. 핀은 날짜 순서대로 행성 둘레에 고르게 배치됨)
 */
public class PlanetWorldView extends View {

    // ------------------------------------
    // 조절할 수 있는 값
    // ------------------------------------

    // 외계인 크기 (눈싸움 화면은 0.34)
    private static final float ALIEN_SCALE_DP = 0.30f;

    private static final int ALIEN_A_FILL = 0xFFBFE0CA;  // 연한 파스텔 초록
    private static final int ALIEN_A_DARK = 0xFFA3CDB0;
    private static final int ALIEN_B_FILL = 0xFFF6C8D4;  // 연한 파스텔 핑크
    private static final int ALIEN_B_DARK = 0xFFE6AEC0;

    // 외계인이 걷는 범위: 중심 각도, 흔들리는 폭(도), 한 바퀴 왕복 시간(ms)
    private static final float A_CENTER_DEG = -28f;
    private static final float A_RANGE_DEG = 55f;
    private static final float A_PERIOD_MS = 19000f;

    private static final float B_CENTER_DEG = 32f;
    private static final float B_RANGE_DEG = 48f;
    private static final float B_PERIOD_MS = 24000f;

    // 걸음 속도(1초에 몇 걸음) / 통통 튀는 높이
    private static final float STEP_HZ = 2.6f;
    private static final float STEP_BOUNCE_DP = 1.8f;

    // 행성이 둥둥 떠 있는 정도: 위아래로 움직이는 폭(dp)과 한 번 오르내리는 시간(ms)
    private static final float FLOAT_DP = 4f;
    private static final float FLOAT_PERIOD_MS = 4200f;

    // 핀 크기 (dp)
    private static final float PIN_HEAD_DP = 19f;
    private static final float PIN_STEM_DP = 11f;

    // 외계인 몸 기준 발바닥 위치 (AlienFightView 와 같음)
    private static final float FEET_Y = 36f;
    private static final float ARM_REST_DEG = 37f;
    private static final float ARM_SWING_DEG = 24f;
    private static final float ARM_LENGTH = 17f;
    private static final float ARM_WIDTH = 10f;

    // ------------------------------------
    // 핀 데이터
    // ------------------------------------

    public static class Pin {

        public final String date;      // "2026-11-05"
        public final int month;        // 11
        public final int day;          // 5
        public final int count;        // 그 날짜의 사진 수
        public final int photoIndex;   // 대표 사진의 목록 위치
        public final String url;       // 대표 사진 주소

        Bitmap thumb;
        BitmapShader shader;

        // 화면 크기에 맞춰 계산되는 값
        float angle;    // 행성 위 위치 (맨 위 0도, 시계 방향)
        float headR;    // 핀 머리 반지름(px)

        public Pin(String date, int month, int day,
                   int count, int photoIndex, String url) {
            this.date = date;
            this.month = month;
            this.day = day;
            this.count = count;
            this.photoIndex = photoIndex;
            this.url = url;
        }
    }

    public interface OnPinClickListener {
        void onPinClick(Pin pin);
    }

    private final List<Pin> pins = new ArrayList<>();
    private String selectedDate = "";
    private OnPinClickListener listener;

    // ------------------------------------
    // 그리기 도구
    // ------------------------------------

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint armPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Path bodyPath = new Path();
    private final Path earPath = new Path();
    private final Path eyePath = new Path();
    private final Path tmpPath = new Path();

    private final RectF rect = new RectF();
    private final Matrix matrix = new Matrix();

    private float density;
    private float unit;

    private float cx;
    private float cy;
    private float radius;
    private float bobY;   // 지금 떠 있는 높이 변화(px)

    // 반짝이 별 위치 (화면 크기 대비 비율)
    private static final float[][] TWINKLES = {
            {0.10f, 0.08f}, {0.88f, 0.12f}, {0.06f, 0.46f},
            {0.94f, 0.55f}, {0.14f, 0.90f}, {0.84f, 0.92f},
            {0.50f, 0.04f}, {0.30f, 0.97f}
    };

    // ------------------------------------
    // 행성 테마 (색 + 표면 장식 + 고리 + 핀 색)
    // ------------------------------------

    public static final class Theme {

        public final String name;
        public final int[] planet;      // 행성 몸 그라데이션 3색 (밝음 -> 어두움)
        final boolean ring;
        final int ringOuter;
        final int ringInner;
        final int patch;                // 땅 무늬
        final int crater;               // 움푹 들어간 곳
        final int rim;                  // 행성 테두리
        final int tw1;                  // 배경 반짝이 색 두 가지
        final int tw2;
        final int[] decor;              // 표면 장식 6종 (순서대로 반복)
        final int pinDark;
        final int pinMid;
        final int pinMain;
        final int pinSoft;

        Theme(String name, int[] planet, boolean ring,
              int ringOuter, int ringInner,
              int patch, int crater, int rim,
              int tw1, int tw2, int[] decor,
              int pinDark, int pinMid, int pinMain, int pinSoft) {

            this.name = name;
            this.planet = planet;
            this.ring = ring;
            this.ringOuter = ringOuter;
            this.ringInner = ringInner;
            this.patch = patch;
            this.crater = crater;
            this.rim = rim;
            this.tw1 = tw1 & 0x00FFFFFF;
            this.tw2 = tw2 & 0x00FFFFFF;
            this.decor = decor;
            this.pinDark = pinDark;
            this.pinMid = pinMid;
            this.pinMain = pinMain;
            this.pinSoft = pinSoft;
        }
    }

    // 장식 번호: 0 꽃, 1 버섯, 2 나무, 3 새싹, 4 깃발, 5 수정,
    //           6 하트, 7 별, 8 눈 쌓인 소나무, 9 눈송이, 10 선인장, 11 사탕
    public static final Theme[] THEMES = {

            new Theme("라벤더",
                    new int[]{0xFFE4DBFF, 0xFFB5A5F5, 0xFF8574DE}, true,
                    0xCCF7B7D8, 0xCCF3C969,
                    0x55FFC2DC, 0x336C58C8, 0x66FFFFFF,
                    0xF3C969, 0x9C8CF0,
                    new int[]{0, 1, 2, 3, 4, 5},
                    0xFF6C58C8, 0xFF9C8CF0, 0xFF7F6BE0, 0xFFEFEAFF),

            new Theme("벚꽃",
                    new int[]{0xFFFFE9F1, 0xFFFFBBD3, 0xFFE985AE}, true,
                    0xCCFFD6E6, 0xCCFFFFFF,
                    0x55FFFFFF, 0x33C2527F, 0x77FFFFFF,
                    0xF7A8CB, 0xFFD7E8,
                    new int[]{6, 0, 7, 6, 0, 3},
                    0xFFC2527F, 0xFFEF9CBC, 0xFFE0679B, 0xFFFFEAF2),

            new Theme("초록 숲",
                    new int[]{0xFFE3F7E8, 0xFFA9DDBB, 0xFF5FB88C}, false,
                    0, 0,
                    0x55F4FFD6, 0x33336B50, 0x66FFFFFF,
                    0x8FD6A8, 0xF3E08A,
                    new int[]{2, 3, 1, 0, 2, 4},
                    0xFF3E8C66, 0xFF84CBA2, 0xFF4FAF7E, 0xFFE9F8EE),

            new Theme("바다",
                    new int[]{0xFFE1F3FF, 0xFF9FD0F7, 0xFF5D9FE3}, true,
                    0xCCFFFFFF, 0xCC9ED6FF,
                    0x66FFFFFF, 0x334170B8, 0x77FFFFFF,
                    0x8CCDFF, 0xFFFFFF,
                    new int[]{5, 7, 4, 5, 7, 0},
                    0xFF3F74B8, 0xFF8CBFEF, 0xFF4F8FDB, 0xFFE8F4FF),

            new Theme("겨울",
                    new int[]{0xFFFFFFFF, 0xFFE1EAFB, 0xFFAEC2E6}, true,
                    0xCCDCE7FA, 0xCCFFFFFF,
                    0x66FFFFFF, 0x335A78B5, 0x99FFFFFF,
                    0xBFD4F5, 0xFFFFFF,
                    new int[]{8, 9, 8, 7, 9, 5},
                    0xFF5A78B5, 0xFF9DB4DE, 0xFF6F8FD0, 0xFFEEF3FF),

            new Theme("노을",
                    new int[]{0xFFFFEBD6, 0xFFFFBE92, 0xFFEC8764}, true,
                    0xCCFFD9A8, 0xCCFFF3C4,
                    0x55FFE0B0, 0x33B5523A, 0x77FFFFFF,
                    0xFFC97A, 0xFF9DB8,
                    new int[]{10, 0, 11, 4, 10, 7},
                    0xFFC2603F, 0xFFF0A27E, 0xFFE0714B, 0xFFFFF0E4)
    };

    private Theme theme = THEMES[0];

    /** 테마 번호로 행성 모양을 바꿈 (범위를 벗어나면 첫 번째 테마) */
    public void setTheme(int index) {

        Theme next = index >= 0 && index < THEMES.length
                ? THEMES[index]
                : THEMES[0];

        if (next != theme) {
            theme = next;
            invalidate();
        }
    }

    public PlanetWorldView(Context context) {
        super(context);
        init();
    }

    public PlanetWorldView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {

        density = getResources().getDisplayMetrics().density;
        unit = ALIEN_SCALE_DP * density;

        setContentDescription("월별 사진이 핀으로 꽂힌 행성");

        // ----- 외계인 몸: 짧은 다리 + U자 홈 (눈싸움 화면과 동일) -----
        bodyPath.moveTo(-19f, 6f);
        bodyPath.cubicTo(-19f, -8f, 19f, -8f, 19f, 6f);
        bodyPath.lineTo(19f, 30f);
        bodyPath.quadTo(19f, 36f, 13f, 36f);
        bodyPath.lineTo(9f, 36f);
        bodyPath.quadTo(4f, 36f, 4f, 30f);
        bodyPath.lineTo(4f, 24f);
        bodyPath.quadTo(0f, 20f, -4f, 24f);
        bodyPath.lineTo(-4f, 30f);
        bodyPath.quadTo(-4f, 36f, -9f, 36f);
        bodyPath.lineTo(-13f, 36f);
        bodyPath.quadTo(-19f, 36f, -19f, 30f);
        bodyPath.close();

        // ----- 귀: 나팔 모양 (왼쪽 기준, 오른쪽은 뒤집어 그림) -----
        earPath.moveTo(-8f, -40f);
        earPath.lineTo(-20f, -55f);
        earPath.lineTo(-28f, -48f);
        earPath.lineTo(-14f, -34f);
        earPath.close();

        // ----- 눈: 위가 평평한 반원 -----
        eyePath.moveTo(-6f, 0f);
        eyePath.arcTo(new RectF(-6f, -6f, 6f, 6f), 180f, -180f);
        eyePath.close();

        armPaint.setStyle(Paint.Style.STROKE);
        armPaint.setStrokeCap(Paint.Cap.ROUND);
        armPaint.setStrokeWidth(ARM_WIDTH);

        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);

        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);
    }

    // ------------------------------------
    // 바깥에서 쓰는 함수
    // ------------------------------------

    public void setOnPinClickListener(OnPinClickListener listener) {
        this.listener = listener;
    }

    public void setPins(List<Pin> newPins) {
        pins.clear();
        pins.addAll(newPins);
        invalidate();
    }

    public void setSelectedDate(String date) {
        selectedDate = date == null ? "" : date;
        invalidate();
    }

    /** 대표 사진 썸네일이 준비되면 호출 */
    public void setPinThumb(Pin pin, Bitmap bitmap) {

        if (pin == null || bitmap == null) return;

        pin.thumb = bitmap;
        pin.shader = new BitmapShader(
                bitmap,
                Shader.TileMode.CLAMP,
                Shader.TileMode.CLAMP
        );

        invalidate();
    }

    // ------------------------------------
    // 크기 계산
    // ------------------------------------

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        cx = w / 2f;
        cy = h / 2f;

        // 핀과 고리가 화면 밖으로 나가지 않는 크기
        float pinSpace = (PIN_HEAD_DP * 2f + PIN_STEM_DP + 8f) * density;

        radius = Math.min(
                Math.min(w / 2f - pinSpace, h / 2f - pinSpace),
                (w / 2f - 8f * density) / 1.5f
        );

        radius = Math.max(radius, 40f * density);
    }

    // ------------------------------------
    // 그리기
    // ------------------------------------

    @Override
    protected void onDraw(Canvas canvas) {

        long now = SystemClock.uptimeMillis();

        int w = getWidth();
        int h = getHeight();

        layoutPins();

        drawTwinkles(canvas, now, w, h);

        // 행성 전체(외계인, 핀 포함)가 둥둥 떠다님
        bobY = (float) Math.sin(2.0 * Math.PI * now / FLOAT_PERIOD_MS)
                * FLOAT_DP * density;

        canvas.save();
        canvas.translate(0f, bobY);

        drawRing(canvas, true);
        drawPlanet(canvas);
        drawDecorations(canvas);
        drawRing(canvas, false);

        drawAlien(canvas, now, A_CENTER_DEG, A_RANGE_DEG, A_PERIOD_MS,
                0.0f, ALIEN_A_FILL, ALIEN_A_DARK);

        drawAlien(canvas, now, B_CENTER_DEG, B_RANGE_DEG, B_PERIOD_MS,
                0.37f, ALIEN_B_FILL, ALIEN_B_DARK);

        for (Pin pin : pins) {
            drawPin(canvas, pin);
        }

        canvas.restore();

        // 걷는 모습이 계속 움직이도록 다음 프레임 예약
        // (화면에서 숨겨지면 onDraw 가 멈추므로 같이 멈춤)
        postInvalidateOnAnimation();
    }

    // ----- 배경 반짝이 -----

    private void drawTwinkles(Canvas canvas, long now, int w, int h) {

        for (int i = 0; i < TWINKLES.length; i++) {

            float t = (float) Math.sin(
                    2.0 * Math.PI * (now / 2600.0 + i * 0.23));

            float alpha = 0.35f + 0.65f * (0.5f + 0.5f * t);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(
                    ((int) (alpha * 200) << 24)
                            | (i % 2 == 0 ? theme.tw1 : theme.tw2));

            drawSparkle(
                    canvas,
                    TWINKLES[i][0] * w,
                    TWINKLES[i][1] * h,
                    (4f + (i % 3)) * density * (0.8f + 0.2f * t)
            );
        }
    }

    // ----- 고리 (뒤쪽 절반은 행성 뒤, 앞쪽 절반은 행성 앞) -----

    private void drawRing(Canvas canvas, boolean back) {

        if (!theme.ring) {
            return;
        }

        float rx = radius * 1.5f;
        float ry = radius * 0.36f;

        canvas.save();
        canvas.translate(cx, cy);
        canvas.rotate(-18f);

        strokePaint.setShader(null);

        // 굵은 분홍 띠
        strokePaint.setStrokeWidth(6f * density);
        strokePaint.setColor(theme.ringOuter);
        rect.set(-rx, -ry, rx, ry);
        canvas.drawArc(rect, back ? 180f : 0f, 180f, false, strokePaint);

        // 안쪽 얇은 금색 띠
        strokePaint.setStrokeWidth(1.8f * density);
        strokePaint.setColor(theme.ringInner);
        rect.set(-rx * 0.93f, -ry * 0.93f, rx * 0.93f, ry * 0.93f);
        canvas.drawArc(rect, back ? 180f : 0f, 180f, false, strokePaint);

        canvas.restore();
    }

    // ----- 행성 몸 -----

    private void drawPlanet(Canvas canvas) {

        // 왼쪽 위에서 빛을 받는 연보라 행성
        // (앞에서 반짝이 별을 그리며 바뀐 투명도가 남아 있으면 행성이
        //  깜빡이며 비치므로, 항상 불투명한 색으로 되돌리고 시작)
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFFFFFFF);
        paint.setShader(new RadialGradient(
                cx - radius * 0.35f,
                cy - radius * 0.40f,
                radius * 1.7f,
                theme.planet,
                new float[]{0f, 0.5f, 1f},
                Shader.TileMode.CLAMP
        ));
        canvas.drawCircle(cx, cy, radius, paint);
        paint.setShader(null);

        // 분홍 무늬 (땅 색이 다른 곳)
        paint.setColor(theme.patch);
        patch(canvas, -0.35f, -0.25f, 0.24f, 0.17f);
        patch(canvas, 0.42f, 0.08f, 0.20f, 0.26f);
        patch(canvas, -0.12f, 0.52f, 0.28f, 0.16f);
        patch(canvas, 0.22f, -0.55f, 0.14f, 0.10f);

        // 움푹 들어간 곳 (귀여운 점 같은 크레이터)
        paint.setColor(theme.crater);
        patch(canvas, -0.55f, 0.15f, 0.10f, 0.07f);
        patch(canvas, 0.05f, -0.10f, 0.12f, 0.08f);
        patch(canvas, 0.58f, -0.35f, 0.07f, 0.05f);
        patch(canvas, 0.30f, 0.62f, 0.09f, 0.06f);

        // 테두리
        strokePaint.setShader(null);
        strokePaint.setStrokeWidth(2f * density);
        strokePaint.setColor(theme.rim);
        canvas.drawCircle(cx, cy, radius - density, strokePaint);
    }

    /** 행성 중심 기준 비율(반지름 단위)로 타원을 그림 */
    private void patch(Canvas canvas, float px, float py, float rx, float ry) {

        rect.set(
                cx + (px - rx) * radius,
                cy + (py - ry) * radius,
                cx + (px + rx) * radius,
                cy + (py + ry) * radius
        );

        canvas.drawOval(rect, paint);
    }

    // ----- 표면 장식: 핀 사이사이(15°, 45°, ...)에 12개 -----

    private void drawDecorations(Canvas canvas) {

        for (int k = 0; k < 12; k++) {

            float angle = 15f + 30f * k;

            canvas.save();
            canvas.translate(cx, cy);
            canvas.rotate(angle);
            canvas.translate(0f, -radius + 1.5f * density);

            drawDecoration(canvas, theme.decor[k % theme.decor.length]);

            canvas.restore();
        }
    }

    /** 땅(0,0)에서 위쪽(-y)으로 자라는 작은 장식 */
    private void drawDecoration(Canvas canvas, int type) {

        float d = density;

        strokePaint.setShader(null);
        paint.setStyle(Paint.Style.FILL);

        switch (type) {

            case 0: // 꽃
                strokePaint.setStrokeWidth(1.6f * d);
                strokePaint.setColor(0xFF7FBF9A);
                canvas.drawLine(0f, 0f, 0f, -8f * d, strokePaint);
                paint.setColor(0xFFFFA6C9);
                canvas.drawCircle(0f, -10f * d, 3.4f * d, paint);
                paint.setColor(0xFFFFE08A);
                canvas.drawCircle(0f, -10f * d, 1.5f * d, paint);
                break;

            case 1: // 버섯
                strokePaint.setStrokeWidth(2.6f * d);
                strokePaint.setColor(0xFFFFF4E6);
                canvas.drawLine(0f, 0f, 0f, -5f * d, strokePaint);
                paint.setColor(0xFFFF9DB8);
                rect.set(-5f * d, -10f * d, 5f * d, -3.5f * d);
                canvas.drawOval(rect, paint);
                paint.setColor(0xFFFFFFFF);
                canvas.drawCircle(-1.8f * d, -7.4f * d, 1.1f * d, paint);
                canvas.drawCircle(1.8f * d, -6.2f * d, 0.9f * d, paint);
                break;

            case 2: // 동글동글 나무
                strokePaint.setStrokeWidth(2.2f * d);
                strokePaint.setColor(0xFFB98E6A);
                canvas.drawLine(0f, 0f, 0f, -7f * d, strokePaint);
                paint.setColor(0xFF9FD8B4);
                canvas.drawCircle(0f, -11f * d, 5.2f * d, paint);
                paint.setColor(0xFFBDE8CB);
                canvas.drawCircle(-1.5f * d, -12.5f * d, 2.2f * d, paint);
                break;

            case 3: // 새싹
                strokePaint.setStrokeWidth(1.6f * d);
                strokePaint.setColor(0xFF7FBF9A);
                canvas.drawLine(0f, 0f, 0f, -6f * d, strokePaint);
                paint.setColor(0xFF9FD8B4);
                rect.set(-6f * d, -9f * d, 0f, -5f * d);
                canvas.drawOval(rect, paint);
                rect.set(0f, -10f * d, 6f * d, -6f * d);
                canvas.drawOval(rect, paint);
                break;

            case 4: // 작은 깃발
                strokePaint.setStrokeWidth(1.6f * d);
                strokePaint.setColor(0xFFFFFFFF);
                canvas.drawLine(0f, 0f, 0f, -13f * d, strokePaint);
                tmpPath.reset();
                tmpPath.moveTo(0f, -13f * d);
                tmpPath.lineTo(7f * d, -10.5f * d);
                tmpPath.lineTo(0f, -8f * d);
                tmpPath.close();
                paint.setColor(0xFFFFE08A);
                canvas.drawPath(tmpPath, paint);
                break;

            case 6: // 하트
                strokePaint.setStrokeWidth(1.6f * d);
                strokePaint.setColor(0xFF7FBF9A);
                canvas.drawLine(0f, 0f, 0f, -4f * d, strokePaint);
                tmpPath.reset();
                tmpPath.moveTo(0f, -5f * d);
                tmpPath.cubicTo(-8f * d, -10f * d, -4.5f * d, -16f * d, 0f, -12f * d);
                tmpPath.cubicTo(4.5f * d, -16f * d, 8f * d, -10f * d, 0f, -5f * d);
                tmpPath.close();
                paint.setColor(0xFFFF7FAE);
                canvas.drawPath(tmpPath, paint);
                break;

            case 7: // 별
                strokePaint.setStrokeWidth(1.6f * d);
                strokePaint.setColor(0xFFFFF4E6);
                canvas.drawLine(0f, 0f, 0f, -6f * d, strokePaint);
                paint.setColor(0xFFFFE08A);
                drawSparkle(canvas, 0f, -10.5f * d, 4.8f * d);
                break;

            case 8: // 눈 쌓인 소나무
                strokePaint.setStrokeWidth(2.2f * d);
                strokePaint.setColor(0xFFB98E6A);
                canvas.drawLine(0f, 0f, 0f, -4f * d, strokePaint);
                tmpPath.reset();
                tmpPath.moveTo(0f, -15f * d);
                tmpPath.lineTo(-5.5f * d, -4f * d);
                tmpPath.lineTo(5.5f * d, -4f * d);
                tmpPath.close();
                paint.setColor(0xFF8CCFA6);
                canvas.drawPath(tmpPath, paint);
                tmpPath.reset();
                tmpPath.moveTo(0f, -15f * d);
                tmpPath.lineTo(-2.6f * d, -10.5f * d);
                tmpPath.lineTo(2.6f * d, -10.5f * d);
                tmpPath.close();
                paint.setColor(0xFFFFFFFF);
                canvas.drawPath(tmpPath, paint);
                break;

            case 9: // 눈송이
                strokePaint.setStrokeWidth(1.4f * d);
                strokePaint.setColor(0xFFFFFFFF);
                canvas.drawLine(0f, 0f, 0f, -3f * d, strokePaint);
                canvas.save();
                canvas.translate(0f, -9f * d);
                for (int s = 0; s < 3; s++) {
                    canvas.drawLine(0f, -5.5f * d, 0f, 5.5f * d, strokePaint);
                    canvas.rotate(60f);
                }
                canvas.restore();
                break;

            case 10: // 선인장
                strokePaint.setStrokeWidth(4f * d);
                strokePaint.setColor(0xFF8CCB9B);
                canvas.drawLine(0f, -1f * d, 0f, -10f * d, strokePaint);
                strokePaint.setStrokeWidth(2.8f * d);
                canvas.drawLine(0f, -5.5f * d, -4.4f * d, -5.5f * d, strokePaint);
                canvas.drawLine(-4.4f * d, -5.5f * d, -4.4f * d, -8.5f * d, strokePaint);
                canvas.drawLine(0f, -4f * d, 4.4f * d, -4f * d, strokePaint);
                canvas.drawLine(4.4f * d, -4f * d, 4.4f * d, -6.8f * d, strokePaint);
                paint.setColor(0xFFFF9DB8);
                canvas.drawCircle(0f, -12.2f * d, 1.7f * d, paint);
                break;

            case 11: // 사탕
                strokePaint.setStrokeWidth(1.6f * d);
                strokePaint.setColor(0xFFFFFFFF);
                canvas.drawLine(0f, 0f, 0f, -8f * d, strokePaint);
                paint.setColor(0xFFFF9DB8);
                canvas.drawCircle(0f, -11.5f * d, 4.2f * d, paint);
                paint.setColor(0xFFFFFFFF);
                canvas.drawCircle(0f, -11.5f * d, 1.7f * d, paint);
                break;

            default: // 반짝 수정
                tmpPath.reset();
                tmpPath.moveTo(0f, -12f * d);
                tmpPath.lineTo(3.6f * d, -6f * d);
                tmpPath.lineTo(0f, 0f);
                tmpPath.lineTo(-3.6f * d, -6f * d);
                tmpPath.close();
                paint.setColor(0xFFB9E3FF);
                canvas.drawPath(tmpPath, paint);
                paint.setColor(0xFFE4F5FF);
                canvas.drawCircle(-0.8f * d, -7.5f * d, 1.1f * d, paint);
                break;
        }
    }

    // ----- 걷는 외계인 -----

    private void drawAlien(
            Canvas canvas,
            long now,
            float centerDeg,
            float rangeDeg,
            float periodMs,
            float phase,
            int fill,
            int dark) {

        double wave = 2.0 * Math.PI * (now / (double) periodMs + phase);

        // 행성 위 각도 (맨 위 = 0도, 시계 방향이 +)
        float angle = centerDeg + rangeDeg * (float) Math.sin(wave);

        // 가는 방향 (+1 = 시계 방향) 과 속도 비율 (방향을 바꿀 때 느려짐)
        float velocity = (float) Math.cos(wave);
        int dir = velocity >= 0f ? 1 : -1;
        float speed = Math.abs(velocity);

        // 걸음 박자: 걷는 속도가 느려지면 걸음도 작아짐
        float step = (float) Math.sin(2.0 * Math.PI * STEP_HZ * now / 1000.0);

        float bounce = Math.abs(step) * STEP_BOUNCE_DP * density
                * (0.35f + 0.65f * speed);

        float tilt = step * 7f * (0.35f + 0.65f * speed);

        float swing = ARM_SWING_DEG * step * (0.35f + 0.65f * speed);

        float earWiggle = 6f * (float) Math.sin(
                2.0 * Math.PI * (now / 900.0 + phase));

        canvas.save();
        canvas.translate(cx, cy);
        canvas.rotate(angle);

        // 발이 행성 표면에 닿도록 위치 (살짝 파묻힘)
        canvas.translate(0f, -radius + 0.8f * density);
        canvas.translate(0f, -FEET_Y * unit - bounce);

        canvas.scale(unit, unit);

        // 가는 쪽으로 살짝 기울이고, 걸음에 맞춰 좌우로 뒤뚱
        canvas.rotate(dir * 5f + tilt, 0f, FEET_Y);

        // 팔은 걸음에 맞춰 엇갈려 흔들림
        armPaint.setColor(dark);
        drawArm(canvas, 1, ARM_REST_DEG + swing);
        drawArm(canvas, -1, ARM_REST_DEG - swing);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(fill);

        drawEar(canvas, 1, earWiggle, fill, dark);
        drawEar(canvas, -1, earWiggle, fill, dark);

        canvas.drawPath(bodyPath, paint);
        canvas.drawCircle(0f, -20f, 25f, paint);

        paint.setColor(0xFF1B1A24);
        drawEye(canvas, -9f, 16f);
        drawEye(canvas, 9f, -16f);

        canvas.restore();
    }

    private void drawArm(Canvas canvas, int side, float degrees) {

        float rad = (float) Math.toRadians(degrees);

        float sx = side * 17f;
        float sy = 5f;

        float ex = sx + side * (float) Math.sin(rad) * ARM_LENGTH;
        float ey = sy + (float) Math.cos(rad) * ARM_LENGTH;

        canvas.drawLine(sx, sy, ex, ey, armPaint);
    }

    private void drawEar(
            Canvas canvas, int side, float wiggleDeg, int fill, int dark) {

        canvas.save();
        canvas.scale(-side, 1f);
        canvas.rotate(wiggleDeg, -11f, -38f);

        paint.setColor(fill);
        canvas.drawPath(earPath, paint);

        canvas.save();
        canvas.rotate(-48f, -24f, -51.5f);
        rect.set(-24f - 6.5f, -51.5f - 3.8f, -24f + 6.5f, -51.5f + 3.8f);
        paint.setColor(dark);
        canvas.drawOval(rect, paint);
        canvas.restore();

        canvas.restore();
    }

    private void drawEye(Canvas canvas, float eyeX, float rotation) {

        canvas.save();
        canvas.translate(eyeX, -22f);
        canvas.rotate(rotation);
        canvas.drawPath(eyePath, paint);
        canvas.restore();
    }

    // ----- 월별 핀 -----

    /**
     * 핀을 행성 둘레에 고르게 배치. 핀이 많으면 서로 겹치지 않게
     * 머리 크기를 줄인다.
     */
    private void layoutPins() {

        int n = pins.size();

        if (n == 0) return;

        float base = PIN_HEAD_DP * density;
        float stem = PIN_STEM_DP * density;

        float spacing = (float) (2.0 * Math.PI * (radius + stem + base) / n);

        float headR = Math.min(base, Math.max(8f * density, spacing * 0.42f));

        for (int i = 0; i < n; i++) {

            Pin pin = pins.get(i);

            pin.angle = 360f * i / n;
            pin.headR = headR;
        }
    }

    /** 핀 머리(동그란 부분) 중심 위치. 사진은 항상 똑바로 보이도록 회전하지 않음 */
    private float headX(Pin pin) {

        double a = Math.toRadians(pin.angle);
        float dist = radius + PIN_STEM_DP * density + pin.headR;

        return cx + (float) Math.sin(a) * dist;
    }

    private float headY(Pin pin) {

        double a = Math.toRadians(pin.angle);
        float dist = radius + PIN_STEM_DP * density + pin.headR;

        return cy - (float) Math.cos(a) * dist;
    }

    private void drawPin(Canvas canvas, Pin pin) {

        float angle = pin.angle;
        boolean selected = pin.date.equals(selectedDate);

        float headR = pin.headR * (selected ? 1.15f : 1f);

        float hx = headX(pin);
        float hy = headY(pin);

        // 꽂힌 바늘 (행성 표면에서 핀 머리까지)
        canvas.save();
        canvas.translate(cx, cy);
        canvas.rotate(angle);

        strokePaint.setShader(null);
        strokePaint.setStrokeWidth(2.4f * density);
        strokePaint.setColor(theme.pinDark);
        canvas.drawLine(
                0f, -radius + 2f * density,
                0f, -radius - (PIN_STEM_DP + 4f) * density,
                strokePaint
        );

        // 땅에 닿는 작은 점
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(theme.pinDark);
        canvas.drawCircle(0f, -radius + 1f * density, 2.6f * density, paint);

        canvas.restore();

        // 바깥 하얀 테두리 (선택되면 금색)
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
        paint.setColor(selected ? 0xFFF3C969 : 0xFFFFFFFF);
        canvas.drawCircle(hx, hy, headR + 2.4f * density, paint);

        if (pin.shader != null && pin.thumb != null) {

            // 대표 사진을 동그랗게
            float size = headR * 2f;

            matrix.reset();
            matrix.setScale(
                    size / pin.thumb.getWidth(),
                    size / pin.thumb.getHeight()
            );
            matrix.postTranslate(hx - headR, hy - headR);

            pin.shader.setLocalMatrix(matrix);

            paint.setShader(pin.shader);
            canvas.drawCircle(hx, hy, headR, paint);
            paint.setShader(null);

        } else {

            // 사진이 아직 오는 중이면 연보라 원 + 반짝이
            paint.setColor(theme.pinSoft);
            canvas.drawCircle(hx, hy, headR, paint);

            paint.setColor(theme.pinMid);
            drawSparkle(canvas, hx, hy, headR * 0.5f);
        }

        // 날짜 라벨 (아래쪽 알약): 핀이 너무 작아지면 생략
        if (headR >= 14f * density) {

            float pillW = 26f * density;
            float pillH = 14f * density;
            float pillY = hy + headR * 0.92f;

            rect.set(hx - pillW / 2f, pillY - pillH / 2f,
                    hx + pillW / 2f, pillY + pillH / 2f);

            paint.setColor(theme.pinMain);
            canvas.drawRoundRect(rect, pillH / 2f, pillH / 2f, paint);

            textPaint.setColor(0xFFFFFFFF);
            textPaint.setTextSize(9.5f * density);
            canvas.drawText(
                    pin.day + "일",
                    hx,
                    pillY + 3.4f * density,
                    textPaint
            );
        }

        // 사진이 2장 이상이면 오른쪽 위에 개수
        if (pin.count > 1 && headR >= 11f * density) {

            float bx = hx + headR * 0.78f;
            float by = hy - headR * 0.78f;
            float br = 7.5f * density;

            paint.setColor(0xFFFF8FB8);
            canvas.drawCircle(bx, by, br, paint);

            textPaint.setTextSize(9f * density);
            canvas.drawText(
                    pin.count > 99 ? "99+" : String.valueOf(pin.count),
                    bx,
                    by + 3.2f * density,
                    textPaint
            );
        }
    }

    // ----- 네 갈래로 반짝이는 별 -----

    private void drawSparkle(Canvas canvas, float x, float y, float r) {

        float k = r * 0.22f;

        tmpPath.reset();
        tmpPath.moveTo(x, y - r);
        tmpPath.quadTo(x + k, y - k, x + r, y);
        tmpPath.quadTo(x + k, y + k, x, y + r);
        tmpPath.quadTo(x - k, y + k, x - r, y);
        tmpPath.quadTo(x - k, y - k, x, y - r);
        tmpPath.close();

        canvas.drawPath(tmpPath, paint);
    }

    // ------------------------------------
    // 핀 터치
    // ------------------------------------

    private Pin pinAt(float x, float touchY) {

        // 떠 있는 만큼 터치 위치를 되돌려서 비교
        float y = touchY - bobY;

        // 위에 그려진 핀(나중에 그린 것)부터 검사
        for (int i = pins.size() - 1; i >= 0; i--) {

            Pin pin = pins.get(i);

            float dx = x - headX(pin);
            float dy = y - headY(pin);

            float hit = Math.max(pin.headR, 12f * density) + 8f * density;

            if (dx * dx + dy * dy <= hit * hit) {
                return pin;
            }
        }

        return null;
    }

    private Pin downPin;

    @Override
    public boolean onTouchEvent(MotionEvent event) {

        switch (event.getAction()) {

            case MotionEvent.ACTION_DOWN:
                downPin = pinAt(event.getX(), event.getY());
                return downPin != null;

            case MotionEvent.ACTION_UP:
                Pin up = pinAt(event.getX(), event.getY());

                if (downPin != null && up == downPin) {

                    if (listener != null) {
                        listener.onPinClick(up);
                    }

                    performClick();
                }

                downPin = null;
                return true;

            case MotionEvent.ACTION_CANCEL:
                downPin = null;
                return true;

            default:
                return downPin != null;
        }
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}

package com.example.starring.court;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

/**
 * 재판장 화면 아래쪽, 화면 밖으로 잘린 행성 위에서
 * 두 외계인이 눈뭉치를 번갈아 던지며 싸우는 장식 View.
 *
 * - 카드/버튼 "뒤", 별 배경 "앞"에 깔아서 사용 (activity_court_vote.xml 참고)
 * - 터치는 받지 않음 (위에 있는 카드/버튼이 그대로 눌림)
 * - 화면에 보일 때만 움직임 (배터리 절약)
 *
 * 모든 외계인 그림은 "로컬 단위"(머리+몸 높이 약 80)로 그린 뒤
 * ALIEN_SCALE_DP 비율로 줄여서 그림. 크기/색/속도는 아래 상수만 수정.
 */
public class AlienFightView extends View {

    // ------------------------------------
    // 조절 상수
    // ------------------------------------

    // 외계인 크기 (로컬 1단위 = 몇 dp). 0.34 = 시안의 1/3 크기
    private static final float ALIEN_SCALE_DP = 0.34f;

    // View 맨 위에서 행성 맨 윗부분(가운데 꼭대기)까지의 거리 (dp)
    private static final float GROUND_FROM_TOP_DP = 100f;

    // 외계인 발이 눈 속에 살짝 파묻히는 정도 (dp). 클수록 더 깊이 서 있는 느낌
    private static final float SINK_DP = 2.5f;

    // 눈 쌓인 층 (행성 윗면을 덮는 하얀 눈)
    private static final int SNOW_COLOR = 0xF2EAF2FF;
    private static final int SNOW_SHADE_COLOR = 0x668FA6D9;
    private static final float SNOW_THICKNESS_DP = 34f;  // 눈 층 평균 두께 (두꺼울수록 푹 쌓인 느낌)

    // 행성(눈밭)을 외계인보다 얼마나 위로 올릴지 (dp). 외계인 위치는 그대로
    private static final float PLANET_RAISE_DP = 14f;

    // 외계인 바깥쪽에 쌓아 둔 눈덩이 더미: 외계인에서 얼마나 떨어뜨릴지(dp)
    private static final float PILE_OFFSET_DP = 28f;
    private static final float PILE_BALL_DP = 4.2f;      // 더미 눈덩이 크기

    // 외계인 가로 위치 (View 너비 대비)
    private static final float LEFT_X_RATIO = 0.31f;
    private static final float RIGHT_X_RATIO = 0.69f;

    // 외계인 색 (왼쪽 = A, 오른쪽 = B). 0xAARRGGBB
    private static final int ALIEN_A_FILL = 0xFF8FB59A;  // 채도 낮은 초록
    private static final int ALIEN_A_DARK = 0xFF6F967B;
    private static final int ALIEN_B_FILL = 0xFFE3A6B8;  // 채도 낮은 핑크
    private static final int ALIEN_B_DARK = 0xFFC4879A;

    // 한 명이 던지는 한 턴의 길이 (ms). 두 명이 번갈아 하므로 전체 주기는 2배
    private static final long TURN_MS = 2200L;

    // 한 턴 안의 타이밍 (ms)
    private static final long WINDUP_MS = 250L;    // 팔을 뒤로 젖히는 시간
    private static final long RELEASE_MS = 400L;   // 눈뭉치를 놓는 순간
    private static final long FLIGHT_MS = 900L;    // 눈뭉치가 날아가는 시간
    private static final long REACT_MS = 600L;     // 맞은 쪽이 휘청이는 시간
    private static final long BURST_MS = 350L;     // 눈 조각이 튀는 시간

    // 눈뭉치 크기(dp)와 포물선 높이(dp)
    private static final float BALL_RADIUS_DP = 4.6f;
    private static final float BALL_ARC_DP = 34f;

    // 가만히 있을 때 통통 튀는 주기(ms)와 높이(dp)
    private static final float BOUNCE_MS = 1000f;
    private static final float BOUNCE_DP = 1.6f;

    // 팔 각도 (아래 방향 0도, 바깥으로 벌어질수록 커지고 180도 = 위)
    private static final float ARM_REST_DEG = 37f;
    private static final float ARM_BACK_DEG = 150f;
    private static final float ARM_RELEASE_DEG = 55f;
    private static final float ARM_LENGTH = 17f;   // 로컬 단위
    private static final float ARM_WIDTH = 10f;    // 로컬 단위

    // 로컬 좌표에서 몸 아래(다리 끝)의 y
    private static final float FEET_Y = 36f;

    // ------------------------------------
    // 그리기 도구 (미리 만들어 두고 재사용)
    // ------------------------------------

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint armPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint earInnerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint eyePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint planetPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint planetRimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint craterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ballPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ballShadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint burstPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint snowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint snowShadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // 외계인 모양 (로컬 단위)
    private final Path bodyPath = new Path();
    private final Path earPath = new Path();
    private final Path eyePath = new Path();

    // 행성 윗면을 덮는 눈 (화면 크기가 정해지면 만듦)
    private final Path snowPath = new Path();

    private final RectF rect = new RectF();

    private float density;
    private float unit;          // 로컬 1단위 = 몇 px

    private float planetCx;
    private float planetCy;
    private float planetR;
    private float groundY;
    private float standGroundY;
    private float standCy;
    private float glowRadius;

    private boolean running = false;

    public AlienFightView(Context context) {
        super(context);
        init();
    }

    public AlienFightView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {

        density = getResources().getDisplayMetrics().density;
        unit = ALIEN_SCALE_DP * density;

        // 터치는 받지 않고, 화면 읽기 도구에서도 건너뜀
        setClickable(false);
        setImportantForAccessibility(
                IMPORTANT_FOR_ACCESSIBILITY_NO
        );

        // ----- 몸: 짧은 다리 두 개 + 가운데 둥근 홈 -----
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

        // ----- 귀(왼쪽 기준, 오른쪽은 좌우 뒤집어서 그림): 나팔 모양 -----
        earPath.moveTo(-8f, -40f);
        earPath.lineTo(-20f, -55f);
        earPath.lineTo(-28f, -48f);
        earPath.lineTo(-14f, -34f);
        earPath.close();

        // ----- 눈: 위가 평평한 반원 -----
        eyePath.moveTo(-6f, 0f);
        eyePath.arcTo(new RectF(-6f, -6f, 6f, 6f), 180f, -180f);
        eyePath.close();

        eyePaint.setColor(0xFF1B1A24);

        armPaint.setStyle(Paint.Style.STROKE);
        armPaint.setStrokeCap(Paint.Cap.ROUND);
        armPaint.setStrokeWidth(ARM_WIDTH);

        craterPaint.setColor(0xB3241D55);

        planetRimPaint.setStyle(Paint.Style.STROKE);
        planetRimPaint.setStrokeWidth(2f * density);
        planetRimPaint.setColor(0x599D8CFF);

        ballPaint.setColor(0xFFFFFFFF);
        ballShadePaint.setColor(0xFFDFE8FF);
        burstPaint.setColor(0xFFFFFFFF);

        snowPaint.setColor(SNOW_COLOR);
        snowShadePaint.setColor(SNOW_SHADE_COLOR);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        // 외계인이 서는 기준선은 고정, 행성만 위로 올림
        standGroundY = GROUND_FROM_TOP_DP * density;
        groundY = standGroundY - PLANET_RAISE_DP * density;

        // 화면 밖으로 잘린 큰 원 (윗부분만 보임)
        planetR = Math.max(w * 0.92f, 200f * density);
        planetCx = w / 2f;
        planetCy = groundY + planetR;
        standCy = standGroundY + planetR;

        planetPaint.setShader(new RadialGradient(
                planetCx,
                groundY + planetR * 0.24f,
                planetR * 1.8f,
                new int[]{0xFF5A4AA8, 0xFF2F2766, 0xFF1A1440},
                new float[]{0f, 0.55f, 1f},
                Shader.TileMode.CLAMP
        ));

        // 지평선을 따라 번지는 은은한 빛
        glowRadius = w * 0.47f;
        glowPaint.setShader(new RadialGradient(
                0f,
                0f,
                glowRadius,
                new int[]{0x8C9D8CFF, 0x009D8CFF},
                new float[]{0f, 1f},
                Shader.TileMode.CLAMP
        ));

        buildSnow(w);
    }

    /**
     * 행성 윗면을 따라 가로로 길게, 아래쪽이 물결처럼 울퉁불퉁한 눈 층을 만듦.
     * (위쪽 선은 행성 곡면과 같고, 아래쪽 선만 두께가 조금씩 달라짐)
     */
    private void buildSnow(int w) {

        snowPath.reset();

        float step = 4f * density;
        float x0 = -8f * density;
        float x1 = w + 8f * density;
        int count = (int) ((x1 - x0) / step) + 1;

        float[] xs = new float[count];
        float[] ys = new float[count];
        float[] bottoms = new float[count];

        for (int i = 0; i < count; i++) {

            float x = x0 + i * step;
            float top = surfaceY(x) - 0.5f * density;

            float thickness = SNOW_THICKNESS_DP * density
                    + 4.5f * density * (float) Math.sin(x / (14f * density))
                    + 2.5f * density * (float) Math.sin(x / (4.3f * density) + 1.3f);

            xs[i] = x;
            ys[i] = top;
            bottoms[i] = top + Math.max(2f * density, thickness);
        }

        snowPath.moveTo(xs[0], ys[0]);
        for (int i = 1; i < count; i++) {
            snowPath.lineTo(xs[i], ys[i]);
        }
        for (int i = count - 1; i >= 0; i--) {
            snowPath.lineTo(xs[i], bottoms[i]);
        }
        snowPath.close();
    }

    /** 가로 위치 x 에서 행성 윗면(눈 표면)의 y */
    private float surfaceY(float x) {

        float dx = x - planetCx;

        if (Math.abs(dx) >= planetR) {
            return planetCy;
        }

        return planetCy
                - (float) Math.sqrt(planetR * planetR - dx * dx);
    }

    /** 가로 위치 x 에 서 있는 외계인의 발바닥 y */
    private float standY(float x) {
        // 외계인 위치는 고정: 행성을 올리기 전 곡면을 기준으로 함
        float dx = x - planetCx;
        float base = Math.abs(dx) >= planetR
                ? standCy
                : standCy - (float) Math.sqrt(planetR * planetR - dx * dx);
        return base + SINK_DP * density;
    }

    // ------------------------------------
    // 화면에 보일 때만 움직임
    // ------------------------------------

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        setRunning(getWindowVisibility() == View.VISIBLE);
    }

    @Override
    protected void onDetachedFromWindow() {
        setRunning(false);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        setRunning(visibility == View.VISIBLE && isAttachedToWindow());
    }

    private void setRunning(boolean value) {
        if (running == value) {
            return;
        }
        running = value;
        if (value) {
            postInvalidateOnAnimation();
        }
    }

    // ------------------------------------
    // 그리기
    // ------------------------------------

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        if (w == 0) {
            return;
        }

        long now = SystemClock.uptimeMillis();

        drawPlanet(canvas, w);

        float leftX = w * LEFT_X_RATIO;
        float rightX = w * RIGHT_X_RATIO;

        // 눈 쌓인 표면 위: 눈 더미와 외계인 바깥쪽에 쌓아 둔 눈덩이
        drawSnowMound(canvas, w * 0.12f, 17f, 5f);
        drawSnowMound(canvas, w * 0.50f, 22f, 6f);
        drawSnowMound(canvas, w * 0.90f, 15f, 4.5f);

        drawPile(canvas, leftX - PILE_OFFSET_DP * density);
        drawPile(canvas, rightX + PILE_OFFSET_DP * density);

        // ----- 누가 던지고 누가 맞는 중인지 계산 -----
        long cycle = now % (TURN_MS * 2L);
        boolean leftThrows = cycle < TURN_MS;
        long tau = leftThrows ? cycle : cycle - TURN_MS;   // 이번 턴 경과 시간

        long hitT = -1L;
        if (tau >= RELEASE_MS + FLIGHT_MS) {
            hitT = tau - (RELEASE_MS + FLIGHT_MS);
        }

        // 왼쪽 = A (오른쪽을 바라봄), 오른쪽 = B (왼쪽을 바라봄)
        drawAlien(canvas, now, leftX, 1,
                ALIEN_A_FILL, ALIEN_A_DARK, 0f,
                leftThrows ? tau : -1L,
                leftThrows ? -1L : hitT);

        drawAlien(canvas, now, rightX, -1,
                ALIEN_B_FILL, ALIEN_B_DARK, 0.5f,
                leftThrows ? -1L : tau,
                leftThrows ? hitT : -1L);

        // ----- 눈뭉치 / 눈 조각 -----
        float throwerX = leftThrows ? leftX : rightX;
        int throwerDir = leftThrows ? 1 : -1;
        float targetX = leftThrows ? rightX : leftX;

        if (tau >= RELEASE_MS && tau < RELEASE_MS + FLIGHT_MS) {
            drawBall(canvas, throwerX, throwerDir, targetX,
                    (tau - RELEASE_MS) / (float) FLIGHT_MS);
        }

        if (hitT >= 0 && hitT < BURST_MS) {
            drawBurst(canvas, targetX, hitT / (float) BURST_MS);
        }

        if (running) {
            postInvalidateOnAnimation();
        }
    }

    private void drawPlanet(Canvas canvas, int w) {

        canvas.drawCircle(planetCx, planetCy, planetR, planetPaint);
        canvas.drawCircle(planetCx, planetCy, planetR, planetRimPaint);

        // 크레이터
        drawCrater(canvas, w * 0.22f, groundY + 34f * density,
                22f * density, 6f * density);
        drawCrater(canvas, w * 0.79f, groundY + 30f * density,
                18f * density, 5f * density);
        drawCrater(canvas, w * 0.53f, groundY + 56f * density,
                26f * density, 6f * density);

        // 눈 층: 아래에 살짝 그림자를 깔고 그 위에 하얀 눈
        canvas.save();
        canvas.translate(0f, 2f * density);
        canvas.drawPath(snowPath, snowShadePaint);
        canvas.restore();

        canvas.drawPath(snowPath, snowPaint);
    }

    /** 눈 표면 위에 봉긋하게 솟은 작은 눈 더미 (rx, ry 는 dp) */
    private void drawSnowMound(Canvas canvas, float cx, float rxDp, float ryDp) {

        float rx = rxDp * density;
        float ry = ryDp * density;
        float cy = surfaceY(cx) + 1f * density;

        rect.set(cx - rx, cy - ry, cx + rx, cy + ry);
        canvas.drawOval(rect, snowPaint);
    }

    /** 눈덩이 3개를 피라미드처럼 쌓은 더미 (바닥 2개 + 위 1개) */
    private void drawPile(Canvas canvas, float cx) {

        float r = PILE_BALL_DP * density;
        float baseY = standY(cx) + 1.5f * density;

        drawPileBall(canvas, cx - r * 1.05f, baseY - r, r);
        drawPileBall(canvas, cx + r * 1.05f, baseY - r, r);
        drawPileBall(canvas, cx, baseY - r * 2.4f, r);
    }

    private void drawPileBall(Canvas canvas, float cx, float cy, float r) {

        canvas.drawCircle(cx, cy, r, ballPaint);
        canvas.drawCircle(
                cx - r * 0.25f, cy - r * 0.25f, r * 0.45f, ballShadePaint
        );
    }

    private void drawCrater(
            Canvas canvas, float cx, float cy, float rx, float ry) {

        rect.set(cx - rx, cy - ry, cx + rx, cy + ry);
        canvas.drawOval(rect, craterPaint);
    }

    // ------------------------------------
    // 외계인 한 명
    // ------------------------------------

    /**
     * @param dir     1 = 오른쪽을 바라봄, -1 = 왼쪽을 바라봄
     * @param phase   통통 튀는 박자를 두 외계인이 다르게 하기 위한 값
     * @param throwT  던지는 중이면 이번 턴 경과 시간(ms), 아니면 -1
     * @param hitT    눈뭉치에 맞은 뒤 경과 시간(ms), 아니면 -1
     */
    private void drawAlien(
            Canvas canvas,
            long now,
            float x,
            int dir,
            int fill,
            int dark,
            float phase,
            long throwT,
            long hitT) {

        // ----- 통통 튀기 / 귀 살랑거림 -----
        float bounce = (0.5f + 0.5f * (float) Math.sin(
                2.0 * Math.PI * (now / BOUNCE_MS + phase)))
                * BOUNCE_DP * density;

        float earWiggle = 6f * (float) Math.sin(
                2.0 * Math.PI * (now / 900f + phase));

        // ----- 팔과 몸 기울기 -----
        float throwArm = ARM_REST_DEG;   // 앞쪽(던지는) 팔
        float backArm = ARM_REST_DEG;    // 반대쪽 팔
        float lean = 0f;                 // 바라보는 쪽으로 기울이는 각도
        float wobble = 0f;               // 맞았을 때 흔들림 각도
        float knockback = 0f;            // 맞았을 때 밀려나는 거리(px)

        if (throwT >= 0) {

            if (throwT < WINDUP_MS) {
                // 팔을 뒤로 번쩍 들며 몸을 젖힘
                float p = smooth(throwT / (float) WINDUP_MS);
                throwArm = lerp(ARM_REST_DEG, ARM_BACK_DEG, p);
                lean = -6f * p;

            } else if (throwT < RELEASE_MS) {
                // 앞으로 휙 휘두름
                float p = (throwT - WINDUP_MS)
                        / (float) (RELEASE_MS - WINDUP_MS);
                throwArm = lerp(ARM_BACK_DEG, ARM_RELEASE_DEG, p);
                lean = lerp(-6f, 8f, p);

            } else if (throwT < RELEASE_MS + 400L) {
                // 천천히 제자리로
                float p = smooth((throwT - RELEASE_MS) / 400f);
                throwArm = lerp(ARM_RELEASE_DEG, ARM_REST_DEG, p);
                lean = 8f * (1f - p);
            }
        }

        if (hitT >= 0 && hitT < REACT_MS) {

            float p = hitT / (float) REACT_MS;
            float decay = 1f - p;

            wobble = 14f * (float) Math.sin(2.0 * Math.PI * p * 2.5) * decay;
            knockback = -dir * 3f * density * (float) Math.sin(Math.PI * p);

            // 깜짝 놀라 양팔을 번쩍
            float up = ARM_REST_DEG
                    + (125f - ARM_REST_DEG) * (float) Math.sin(Math.PI * p);
            throwArm = up;
            backArm = up;
        }

        // ----- 몸 그리기 -----
        float originY = standY(x) - FEET_Y * unit - bounce;

        canvas.save();
        canvas.translate(x + knockback, originY);
        canvas.scale(unit, unit);

        // 발끝(0, FEET_Y)을 기준으로 기울임
        canvas.rotate(lean * dir + wobble, 0f, FEET_Y);

        // 팔 (몸 뒤)
        armPaint.setColor(dark);
        drawArm(canvas, dir, throwArm);    // 앞쪽 팔: 바라보는 쪽
        drawArm(canvas, -dir, backArm);    // 뒤쪽 팔

        fillPaint.setColor(fill);
        earInnerPaint.setColor(dark);

        // 귀
        drawEar(canvas, 1, earWiggle);
        drawEar(canvas, -1, earWiggle);

        // 몸, 머리
        canvas.drawPath(bodyPath, fillPaint);
        canvas.drawCircle(0f, -20f, 25f, fillPaint);

        // 화난 반원 눈 (안쪽이 아래로 내려오게 기울임)
        drawEye(canvas, -9f, 16f);
        drawEye(canvas, 9f, -16f);

        canvas.restore();
    }

    /** side: 1 = 오른쪽 어깨, -1 = 왼쪽 어깨. 아래 방향이 0도 */
    private void drawArm(Canvas canvas, int side, float degrees) {

        float rad = (float) Math.toRadians(degrees);

        float sx = side * 17f;
        float sy = 5f;

        float ex = sx + side * (float) Math.sin(rad) * ARM_LENGTH;
        float ey = sy + (float) Math.cos(rad) * ARM_LENGTH;

        canvas.drawLine(sx, sy, ex, ey, armPaint);
    }

    /** side: 1 = 왼쪽 귀(원본), -1 = 오른쪽 귀(좌우 반전) */
    private void drawEar(Canvas canvas, int side, float wiggleDeg) {

        canvas.save();
        canvas.scale(-side, 1f);

        // 머리와 붙은 부분을 기준으로 살랑
        canvas.rotate(wiggleDeg, -11f, -38f);

        canvas.drawPath(earPath, fillPaint);

        canvas.save();
        canvas.rotate(-48f, -24f, -51.5f);
        rect.set(-24f - 6.5f, -51.5f - 3.8f, -24f + 6.5f, -51.5f + 3.8f);
        canvas.drawOval(rect, earInnerPaint);
        canvas.restore();

        canvas.restore();
    }

    private void drawEye(Canvas canvas, float cx, float rotation) {

        canvas.save();
        canvas.translate(cx, -22f);
        canvas.rotate(rotation);
        canvas.drawPath(eyePath, eyePaint);
        canvas.restore();
    }

    // ------------------------------------
    // 눈뭉치와 눈 조각
    // ------------------------------------

    private void drawBall(
            Canvas canvas,
            float throwerX,
            int throwerDir,
            float targetX,
            float u) {

        // 던지는 쪽, 맞는 쪽 발 높이 (행성 곡면 때문에 조금씩 다름)
        float throwerOriginY = standY(throwerX) - FEET_Y * unit;
        float targetOriginY = standY(targetX) - FEET_Y * unit;

        // 손을 놓는 위치 (팔을 앞으로 뻗은 끝)
        float rad = (float) Math.toRadians(ARM_RELEASE_DEG);
        float handX = throwerX
                + throwerDir * (17f + (float) Math.sin(rad) * ARM_LENGTH) * unit;
        float handY = throwerOriginY
                + (5f + (float) Math.cos(rad) * ARM_LENGTH) * unit;

        // 맞는 위치 (상대 머리)
        float headX = targetX;
        float headY = targetOriginY - 20f * unit;

        float bx = lerp(handX, headX, u);
        float by = lerp(handY, headY, u)
                - BALL_ARC_DP * density * 4f * u * (1f - u);

        float r = BALL_RADIUS_DP * density;

        canvas.drawCircle(bx, by, r, ballPaint);
        canvas.drawCircle(
                bx - r * 0.25f, by - r * 0.25f, r * 0.45f, ballShadePaint
        );
    }

    private void drawBurst(Canvas canvas, float targetX, float p) {

        float cx = targetX;
        float cy = standY(targetX) - FEET_Y * unit - 20f * unit;

        float dist = 16f * density * smooth(p);
        float radius = 2f * density * (1f - p);

        burstPaint.setAlpha((int) (255 * (1f - p)));

        for (int i = 0; i < 7; i++) {

            double angle = (2.0 * Math.PI * i / 7.0) + 0.4;

            canvas.drawCircle(
                    cx + (float) Math.cos(angle) * dist,
                    cy + (float) Math.sin(angle) * dist,
                    radius,
                    burstPaint
            );
        }
    }

    // ------------------------------------
    // 계산 도우미
    // ------------------------------------

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /** 0~1 구간을 부드럽게 (시작과 끝이 천천히) */
    private static float smooth(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return t * t * (3f - 2f * t);
    }
}

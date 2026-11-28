package com.example.starring;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.RadialGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Random;
import java.util.List;

/**
 * 태양 중심 궤도 메뉴.
 *
 * - 태양(하단 중앙)을 중심으로 얇은 동심원 궤도 N개를 그립니다.
 *   고리는 태양에 가까울수록 따뜻한 빛, 멀수록 차가운 빛이고, 번지는 빛(글로우)이 있으며 양 끝으로 갈수록 흐려집니다.
 * - 태양 주변에 은은한 빛무리, 화성~목성 사이에 소행성대, 배경에 작은 별 반짝임을 그립니다.
 * - 행성 N개를 안쪽 궤도부터(= 실제 태양계 순서) 궤도 하나에 하나씩, 고리마다 다른 각도로 흩어 올려놓습니다
 *   (ORBIT_ANGLES_DEG 표 참고).
 * - 행성은 태양을 마주 보는 쪽이 밝고, 태양 반대쪽 가장자리에 그림자가 집니다(PlanetImageView).
 * - 화면이 작아서 행성이 서로 겹치게 되면 행성 크기를 자동으로 줄여서(SIZE_MULTIPLIER → SIZE_MULTIPLIER_MIN) 맞춥니다.
 * - 열 때 고리와 행성이 같은 애니메이션 하나로 동시에 나타납니다(행성이 고리를 타고 퍼져 나감).
 * - 고리 일렁임 모션은 WAVE_ENABLED 로 켜고 끌 수 있습니다(현재 꺼짐).
 */
public class SolarOrbitMenuView extends FrameLayout {

    public interface OnItemSelectedListener {
        void onItemSelected(int index);
    }

    /** 메뉴 항목 하나 */
    public static class Item {
        final int imageRes;      // 행성 PNG
        final String planetName; // 수성, 금성 ... (화면에는 안 보이고 접근성 설명에만 사용)
        final String action;     // 행성 생성, 블랙홀 생성 ... (행성 아래에 표시되는 이름)

        public Item(int imageRes, String planetName, String action) {
            this.imageRes = imageRes;
            this.planetName = planetName;
            this.action = action;
        }
    }

    // ---- 크기 상수 ----
    private static final float ICON_DP = 56f;              // 행성 기본 크기 (지구 기준, 배율 1.0)
    private static final float SIZE_MULTIPLIER = 1.4f;     // 전체 행성 크기 배율 (기본 56dp의 1.4배)
    private static final float SIZE_MULTIPLIER_MIN = 1.0f; // 작은 화면에서 줄일 수 있는 최소 배율
    private static final float SIZE_MULTIPLIER_STEP = 0.05f;
    private static final float MIN_GAP_DP = 6f;            // 행성/이름 사이 최소 간격

    // 행성별 크기 배율 (실제 크기 차이를 "약간씩"만 반영)
    // 수성, 금성, 지구, 화성, 목성, 토성, 천왕성, 해왕성
    private static final float[] SIZE_SCALE = {
            0.70f, 0.90f, 0.95f, 0.80f, 1.30f, 1.20f, 1.00f, 1.00f
    };

    private static final float NAME_SP = 13f;              // 행성 아래 이름 글자 크기
    private static final float NAME_MAX_WIDTH_DP = 84f;    // 이름 최대 폭 (넘으면 2줄)
    private static final float NAME_GAP_DP = 3f;           // 행성과 이름 사이 간격

    // ---- 그림자 상수 ----
    private static final boolean SHADOW_ENABLED = true;
    private static final int SHADOW_COLOR = 0x0A0A1E;      // 그림자 색 (RGB)
    private static final int SHADOW_MAX_ALPHA = 0xB0;      // 가장 어두운 가장자리의 진하기 (0~255)
    private static final float SHADOW_START = 0.45f;       // 0~1: 클수록 밝은 면이 넓음(그림자가 가장자리에만)

    // 행성 빛 닿는 쪽(태양 방향) 가장자리 하이라이트
    private static final int RIM_COLOR = 0xFFF1D6;         // 하이라이트 색 (RGB)
    private static final int RIM_MAX_ALPHA = 0x58;         // 하이라이트 진하기 (0~255)

    // ---- 고리 모양 상수 ----
    private static final float ARC_STROKE_DP = 0.9f;       // 고리 중심선 두께
    private static final int ARC_ALPHA = 200;              // 고리 중심선 진하기 (0~255)
    private static final boolean RING_DASHED = false;      // true: 점선 / false: 실선 (사진처럼 가는 실선이 기본)
    private static final float DASH_ON_DP = 4f;            // 점선일 때: 선 길이
    private static final float DASH_OFF_DP = 5f;           // 점선일 때: 빈 길이
    private static final int RING_COLOR_INNER = 0xFFFFE6B8; // 태양에 가까운 고리 색 (따뜻한 빛)
    private static final int RING_COLOR_OUTER = 0xFFC4DCFF; // 먼 고리 색 (차가운 빛)
    private static final int RING_END_ALPHA = 0x2A;         // 고리 양 끝이 흐려지는 정도 (0 = 완전히 사라짐, 255 = 안 흐려짐)

    // ---- 고리 발광(글로우): 바깥으로 갈수록 넓고 흐린 선 ----
    private static final boolean GLOW_ENABLED = true;
    private static final float[][] GLOW_LAYERS = {         // {두께(dp), 진하기(0~255)}
            {18f, 8f},
            {11f, 16f},
            {6.5f, 30f},
            {3.5f, 52f}
    };
    private static final float GLOW_OUTER_FADE = 0.40f;    // 바깥 고리일수록 빛이 약해지는 정도 (0 = 균일)

    // ---- 태양 빛무리 / 소행성대 / 별 ----
    private static final boolean SUN_GLOW_ENABLED = true;
    private static final float SUN_GLOW_STRENGTH = 1.0f;   // 태양 빛무리 세기 (0~1)
    private static final float SUN_GLOW_RADIUS_FACTOR = 2.0f;  // 바깥 옅은 빛의 범위 (태양 반지름의 몇 배까지). 작을수록 좁음
    private static final float SUN_CORE_RADIUS_FACTOR = 1.45f; // 태양 바로 둘레 진한 빛의 범위
    private static final boolean BELT_ENABLED = true;
    private static final int BELT_AFTER_RING = 3;          // 이 번호(0부터) 고리와 다음 고리 사이에 소행성대 (3 = 화성~목성)
    private static final int BELT_COUNT = 380;             // 소행성 알갱이 개수
    private static final boolean STARS_ENABLED = true;
    private static final int STAR_COUNT = 30;              // 작은 별 개수
    private static final int SPARKLE_COUNT = 6;            // 십자로 반짝이는 큰 별 개수
    private static final boolean PLANET_HALO_ENABLED = false; // 행성 둘레 발광(대기빛): 현재 꺼짐
    private static final float PLANET_HALO_ALPHA = 0.9f;   // 켰을 때 행성 둘레 빛 세기 (0~1)

    // ---- 배치 상수 ----
    // 안쪽 궤도부터(수성~해왕성) 행성이 놓일 각도. 오른쪽 수평선이 0°, 바로 위가 90°, 왼쪽 수평선이 180°.
    // 폭 360~412dp, 높이 780~915dp 화면에서 서로 겹치지 않도록 계산해 둔 값.
    private static final float[] ORBIT_ANGLES_DEG = {
            160f,  // 수성: 왼쪽 아래, 태양 바로 옆
            95f,   // 금성: 위쪽
            60f,   // 지구: 오른쪽 위
            115f,  // 화성: 왼쪽 위
            90f,   // 목성: 정중앙 위
            70f,   // 토성: 오른쪽
            105f,  // 천왕성: 왼쪽
            90f    // 해왕성: 가장 위
    };
    private static final float EDGE_MARGIN_DP = 8f;        // 화면 좌우 가장자리 여백
    private static final float SUN_RADIUS_DP = 52f;        // 열렸을 때 태양 반지름(84dp × 1.25 / 2)
    private static final float FIRST_ORBIT_DP = 110f;      // 가장 안쪽 궤도 반지름
    private static final float MAX_ORBIT_STEP_DP = 70f;    // 궤도 간격(최대)
    private static final float MIN_ORBIT_STEP_DP = 36f;    // 궤도 간격(최소)
    private static final float TOP_RESERVE_DP = 90f;       // 상단(상태바·은하계 이름) 비워둘 높이

    // ---- 모션 상수 ----
    private static final long OPEN_MS = 380;
    private static final long CLOSE_MS = 240;
    private static final boolean WAVE_ENABLED = false;     // 고리 일렁임 켜기/끄기
    private static final float WAVE_AMP = 0.014f;
    private static final float WAVE_STEP = 0.55f;
    private static final long WAVE_PERIOD_MS = 3600;

    private final List<Item> data = new ArrayList<>();
    private final List<View> itemViews = new ArrayList<>();
    // [sh] 블랙홀 잠금: 잠긴 항목은 흐리게만 표시 (클릭 차단은 GalaxyActivity가 담당)
    private static final float LOCKED_ALPHA = 0.35f;
    private boolean[] locked = new boolean[0];
    private final List<PlanetImageView> icons = new ArrayList<>();
    private float[] labelW = new float[0];   // 이름 가로 폭 추정 (px)
    private float[] labelH = new float[0];   // 이름 세로 높이 추정 (px)

    private float[] orbitRadius = new float[0];
    private float[] itemCx = new float[0];
    private float[] itemCy = new float[0];

    private float sizeMultiplier = SIZE_MULTIPLIER;  // 현재 화면에 맞춰 정해진 배율

    private float sunCx;
    private float sunCy;
    private int sunFromBottomPx = 0;

    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint haloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint beltPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint starPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // 화면 크기가 정해지면 만들어 두는 효과들
    private Shader[] ringShaders = new Shader[0];
    private Shader[] planetHaloShaders = new Shader[0];
    private float[] planetHaloRadius = new float[0];
    private Shader sunHaloShader;
    private Shader sunCoreShader;
    private float sunHaloRadius;
    private float sunCoreRadius;

    // 소행성대 (미리 정해 둔 무작위 값)
    private final float[] beltCos = new float[BELT_COUNT];
    private final float[] beltSin = new float[BELT_COUNT];
    private final float[] beltRadial = new float[BELT_COUNT];
    private final float[] beltSize = new float[BELT_COUNT];
    private final int[] beltAlpha = new int[BELT_COUNT];
    private final int[] beltColor = new int[BELT_COUNT];

    // 배경 별 (화면 비율 위치)
    private final float[] starX = new float[STAR_COUNT];
    private final float[] starY = new float[STAR_COUNT];
    private final float[] starSize = new float[STAR_COUNT];
    private final int[] starAlpha = new int[STAR_COUNT];
    private final RectF arcRect = new RectF();

    private float progress = 0f;   // 0 = 닫힘, 1 = 완전히 열림 (고리·행성 공통)
    private float wavePhase = 0f;
    private ValueAnimator progressAnimator;
    private ValueAnimator waveAnimator;
    private boolean open = false;

    private OnItemSelectedListener listener;

    public SolarOrbitMenuView(Context context) {
        super(context);
        init();
    }

    public SolarOrbitMenuView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setWillNotDraw(false);
        float density = getResources().getDisplayMetrics().density;

        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeWidth(density * ARC_STROKE_DP);
        if (RING_DASHED) {
            arcPaint.setPathEffect(new DashPathEffect(
                    new float[]{DASH_ON_DP * density, DASH_OFF_DP * density}, 0f));
        }

        glowPaint.setStyle(Paint.Style.STROKE);

        haloPaint.setStyle(Paint.Style.FILL);
        beltPaint.setStyle(Paint.Style.FILL);
        starPaint.setStyle(Paint.Style.FILL);
        starPaint.setStrokeCap(Paint.Cap.ROUND);

        // 소행성대 / 별: 고정된 무작위 값 (항상 같은 모양)
        Random rnd = new Random(7);
        int[] beltColors = {0xFFE6D5B8, 0xFFFFF4E0, 0xFFB9A98F, 0xFFD8E4FF};
        for (int k = 0; k < BELT_COUNT; k++) {
            double ang = Math.toRadians(180.0 + rnd.nextFloat() * 180.0);   // 위쪽 반원
            beltCos[k] = (float) Math.cos(ang);
            beltSin[k] = (float) Math.sin(ang);
            beltRadial[k] = rnd.nextFloat() + rnd.nextFloat() - 1f;          // -1~1, 가운데에 몰림
            beltSize[k] = 0.5f + rnd.nextFloat() * rnd.nextFloat() * 1.7f;   // dp
            beltAlpha[k] = 70 + rnd.nextInt(170);
            beltColor[k] = beltColors[rnd.nextInt(beltColors.length)];
        }
        for (int k = 0; k < STAR_COUNT; k++) {
            starX[k] = rnd.nextFloat();
            starY[k] = rnd.nextFloat() * 0.92f;
            starSize[k] = 0.6f + rnd.nextFloat() * 1.4f;                     // dp
            starAlpha[k] = 80 + rnd.nextInt(150);
        }
    }

    // ------------------------------------
    // 설정
    // ------------------------------------

    /** 태양 중심이 화면(이 뷰) 아래쪽에서 몇 px 위에 있는지 */
    public void setSunCenterFromBottom(int px) {
        sunFromBottomPx = px;
        requestLayout();
    }

    /** [sh] 블랙홀 잠금: index번 항목을 흐리게 표시할지 */
    public void setItemLocked(int index, boolean isLocked) {
        if (index < 0 || index >= locked.length || locked[index] == isLocked) return;
        locked[index] = isLocked;
        applyState();
    }

    public void setOnItemSelectedListener(OnItemSelectedListener l) {
        listener = l;
    }

    public boolean isOpen() {
        return open;
    }

    public void setItems(List<Item> items) {
        removeAllViews();
        itemViews.clear();
        icons.clear();
        data.clear();
        data.addAll(items);

        int n = data.size();
        locked = new boolean[n];   // [sh] 블랙홀 잠금
        labelW = new float[n];
        labelH = new float[n];

        // 이름 크기 추정용 (줄바꿈 계산)
        Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
        tp.setTextSize(NAME_SP * getResources().getDisplayMetrics().scaledDensity);
        tp.setFakeBoldText(true);
        float lineH = tp.descent() - tp.ascent();
        float maxW = dp(NAME_MAX_WIDTH_DP);

        for (int i = 0; i < n; i++) {
            final int index = i;
            View v = createItemView(data.get(i));
            v.setOnClickListener(view -> {
                if (!open) return; // 닫히는 중에는 무시
                if (listener != null) listener.onItemSelected(index);
            });
            addView(v, new LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
            itemViews.add(v);

            float textW = tp.measureText(data.get(i).action);
            boolean twoLines = textW > maxW;
            labelW[i] = twoLines ? maxW : textW;
            labelH[i] = (twoLines ? 2 : 1) * lineH;
        }

        orbitRadius = new float[n];
        itemCx = new float[n];
        itemCy = new float[n];

        applyState();
        requestLayout();
    }

    /** 행성 PNG(그림자 포함) + 아래에 이름 */
    private View createItemView(Item item) {
        LinearLayout box = new LinearLayout(getContext());
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setClickable(true);
        box.setFocusable(true);
        box.setContentDescription(item.planetName + " " + item.action);

        PlanetImageView icon = new PlanetImageView(getContext());
        icon.setImageResource(item.imageRes);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        icon.setContentDescription(null);
        int iconPx = iconSizePx(icons.size(), sizeMultiplier);
        box.addView(icon, new LinearLayout.LayoutParams(iconPx, iconPx));
        icons.add(icon);

        TextView name = new TextView(getContext());
        name.setText(item.action);
        name.setTextSize(NAME_SP);
        name.setTextColor(Color.WHITE);
        name.setShadowLayer(dp(3), 0, dp(1), Color.BLACK);
        name.setGravity(Gravity.CENTER);
        name.setMaxWidth(dp(NAME_MAX_WIDTH_DP));
        name.setMaxLines(2);
        name.getPaint().setFakeBoldText(true);

        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        nameLp.topMargin = dp(NAME_GAP_DP);
        box.addView(name, nameLp);

        return box;
    }

    // ------------------------------------
    // 측정: 화면 크기에 맞는 행성 크기 배율 결정
    // ------------------------------------

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        int h = MeasureSpec.getSize(heightMeasureSpec);

        if (w > 0 && h > 0 && !itemViews.isEmpty()) {
            sizeMultiplier = fitMultiplier(w, h);

            for (int i = 0; i < icons.size(); i++) {
                PlanetImageView icon = icons.get(i);
                int px = iconSizePx(i, sizeMultiplier);
                LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) icon.getLayoutParams();
                if (lp.width != px || lp.height != px) {
                    lp.width = px;
                    lp.height = px;
                    // 부모(이 뷰)가 이미 측정 중이므로 requestLayout 대신 이 상자만 다시 측정되게 표시
                    itemViews.get(i).forceLayout();
                    icon.forceLayout();
                }
            }
        }

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    /** 행성끼리/이름과 겹치지 않는 가장 큰 배율을 찾는다 (큰 화면은 SIZE_MULTIPLIER 그대로) */
    private float fitMultiplier(int w, int h) {
        float m = SIZE_MULTIPLIER;
        while (m > SIZE_MULTIPLIER_MIN + 0.0001f) {
            if (minGap(w, h, m) >= dp(MIN_GAP_DP)) {
                return m;
            }
            m -= SIZE_MULTIPLIER_STEP;
        }
        return SIZE_MULTIPLIER_MIN;
    }

    // ------------------------------------
    // 배치
    // ------------------------------------

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int w = r - l;
        int h = b - t;

        computePositions(w, h, sizeMultiplier);

        for (int i = 0; i < itemViews.size(); i++) {
            View v = itemViews.get(i);
            int vw = v.getMeasuredWidth();
            int vh = v.getMeasuredHeight();
            int cx = Math.round(itemCx[i]);
            int cy = Math.round(itemCy[i]);
            int iconHalf = iconSizePx(i, sizeMultiplier) / 2;

            // (cx, cy) = 행성 PNG의 중심 = 궤도(고리) 위의 점. 이름은 행성 아래로 늘어짐
            int top = cy - iconHalf;
            v.layout(cx - vw / 2, top, cx + vw / 2, top + vh);

            // 크기 애니메이션 기준점 = 행성 중심
            v.setPivotX(vw / 2f);
            v.setPivotY(iconHalf);

            // 그림자 방향 = 태양에서 행성으로 향하는 방향 (태양 반대쪽이 어두워짐)
            float dx = itemCx[i] - sunCx;
            float dy = itemCy[i] - sunCy;
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            if (len > 0f) {
                icons.get(i).setShadowDirection(dx / len, dy / len);
            }
        }

        buildEffects(w, h);
        applyState();
    }

    /**
     * 궤도 반지름은 일정 간격으로 늘리고, 행성은 각 궤도 위의 정해진 각도(ORBIT_ANGLES_DEG)에 놓는다.
     * 화면 좌우를 벗어나는 바깥 궤도는 각도를 위쪽으로 당겨서 화면 안에 들어오게 한다.
     * 결과는 itemCx / itemCy / orbitRadius / sunCx / sunCy 에 저장된다.
     */
    private void computePositions(int w, int h, float multiplier) {
        int n = itemViews.size();
        if (n == 0) return;

        sunCx = w / 2f;
        sunCy = h - sunFromBottomPx;

        float r0 = dp(FIRST_ORBIT_DP);
        float lastHalf = iconSizePx(n - 1, multiplier) / 2f;

        // 화면 높이에 맞춰 궤도 간격 조절 (가장 바깥 궤도의 꼭대기가 상단 여백을 넘지 않게)
        float hMax = sunCy - dp(TOP_RESERVE_DP) - lastHalf;
        float step = n > 1 ? (hMax - r0) / (n - 1) : dp(MAX_ORBIT_STEP_DP);
        step = Math.min(dp(MAX_ORBIT_STEP_DP), step);
        step = Math.max(dp(MIN_ORBIT_STEP_DP), step);

        for (int i = 0; i < n; i++) {
            float radius = r0 + step * i;
            orbitRadius[i] = radius;

            float angleDeg = ORBIT_ANGLES_DEG[i % ORBIT_ANGLES_DEG.length];
            double theta = Math.toRadians(angleDeg);

            float x = (float) (radius * Math.cos(theta));

            // 행성 또는 이름이 화면 좌우로 잘리지 않게
            float halfW = Math.max(iconSizePx(i, multiplier), labelW[i]) / 2f;
            float xMax = w / 2f - halfW - dp(EDGE_MARGIN_DP);
            if (Math.abs(x) > xMax) {
                x = Math.copySign(xMax, x);
                theta = Math.acos(Math.max(-1f, Math.min(1f, x / radius)));
            }
            float height = (float) (radius * Math.sin(theta));

            itemCx[i] = sunCx + x;
            itemCy[i] = sunCy - height;
        }
    }

    /** 현재 배치에서 행성·이름·태양 사이의 가장 작은 간격(px). 음수면 겹침 */
    private float minGap(int w, int h, float multiplier) {
        computePositions(w, h, multiplier);

        int n = itemViews.size();
        float worst = Float.MAX_VALUE;
        float sunR = dp(SUN_RADIUS_DP);
        float nameGap = dp(NAME_GAP_DP);

        for (int i = 0; i < n; i++) {
            float ri = iconSizePx(i, multiplier) / 2f;

            // 이름 상자 (행성 아래)
            float[] li = labelRect(i, ri, nameGap);

            worst = Math.min(worst, dist(itemCx[i], itemCy[i], sunCx, sunCy) - sunR - ri - dp(2));
            worst = Math.min(worst, rectCircleGap(li, sunCx, sunCy, sunR));

            for (int j = i + 1; j < n; j++) {
                float rj = iconSizePx(j, multiplier) / 2f;
                float[] lj = labelRect(j, rj, nameGap);

                worst = Math.min(worst, dist(itemCx[i], itemCy[i], itemCx[j], itemCy[j]) - ri - rj);
                worst = Math.min(worst, rectCircleGap(li, itemCx[j], itemCy[j], rj));
                worst = Math.min(worst, rectCircleGap(lj, itemCx[i], itemCy[i], ri));
                worst = Math.min(worst, rectRectGap(li, lj));
            }
        }
        return worst;
    }

    private float[] labelRect(int i, float iconHalf, float nameGap) {
        float left = itemCx[i] - labelW[i] / 2f;
        float top = itemCy[i] + iconHalf + nameGap;
        return new float[]{left, top, left + labelW[i], top + labelH[i]};
    }

    private static float dist(float x1, float y1, float x2, float y2) {
        return (float) Math.hypot(x1 - x2, y1 - y2);
    }

    private static float rectCircleGap(float[] r, float cx, float cy, float rad) {
        float dx = Math.max(Math.max(r[0] - cx, 0f), cx - r[2]);
        float dy = Math.max(Math.max(r[1] - cy, 0f), cy - r[3]);
        return (float) Math.hypot(dx, dy) - rad;
    }

    private static float rectRectGap(float[] a, float[] b) {
        float gx = Math.max(a[0] - b[2], b[0] - a[2]);
        float gy = Math.max(a[1] - b[3], b[1] - a[3]);
        if (gx < 0f && gy < 0f) return Math.max(gx, gy);
        return (float) Math.hypot(Math.max(gx, 0f), Math.max(gy, 0f));
    }

    // ------------------------------------
    // 열림 정도(progress) + 일렁임(wave)을 고리/행성에 동시에 반영
    // ------------------------------------

    /** i번째 고리의 현재 배율: 열림 정도 × (1 + 일렁임) */
    private float ringFactor(int i) {
        float wobble = WAVE_ENABLED
                ? WAVE_AMP * (float) Math.sin(wavePhase - i * WAVE_STEP) * progress
                : 0f;
        return progress * (1f + wobble);
    }

    private void applyState() {
        float itemScale = itemScale();
        float itemAlpha = itemAlpha();

        for (int i = 0; i < itemViews.size(); i++) {
            View v = itemViews.get(i);
            float f = ringFactor(i);

            // 행성은 태양에서 뻗은 직선 위에서 고리 반지름과 똑같은 비율로 이동 → 항상 고리 위
            v.setTranslationX((itemCx[i] - sunCx) * (f - 1f));
            v.setTranslationY((itemCy[i] - sunCy) * (f - 1f));
            v.setScaleX(itemScale);
            v.setScaleY(itemScale);
            v.setAlpha(i < locked.length && locked[i] ? itemAlpha * LOCKED_ALPHA : itemAlpha);   // [sh] 블랙홀 잠금
        }

        invalidate();
    }

    // ------------------------------------
    // 그리기: 동심원 궤도 (위쪽 반원)
    // ------------------------------------

    /** 화면 크기가 정해진 뒤 한 번 만들어 두는 그라디언트들 (그릴 때마다 새로 만들지 않기 위해) */
    private void buildEffects(int w, int h) {
        int n = orbitRadius.length;
        if (n == 0) return;

        // 고리: 태양에 가까우면 따뜻한 빛, 멀면 차가운 빛 / 위쪽(12시)이 가장 진하고 양 끝은 흐려짐
        ringShaders = new Shader[n];
        for (int i = 0; i < n; i++) {
            float t = n > 1 ? (float) i / (n - 1) : 0f;
            int base = lerpColor(RING_COLOR_INNER, RING_COLOR_OUTER, t);
            int top = withAlpha(base, 0xFF);
            int end = withAlpha(base, RING_END_ALPHA);
            ringShaders[i] = new SweepGradient(sunCx, sunCy,
                    new int[]{end, end, top, end},
                    new float[]{0f, 0.5f, 0.75f, 1f});
        }

        // 태양 빛무리: 넓고 옅은 것 + 태양 바로 둘레의 진한 것
        // (범위를 태양 가까이로 좁혀서 안개처럼 퍼 보이지 않게)
        sunHaloRadius = dp(SUN_RADIUS_DP) * SUN_GLOW_RADIUS_FACTOR;
        sunHaloShader = new RadialGradient(sunCx, sunCy, sunHaloRadius,
                new int[]{0x55FFB050, 0x1AFF9A40, 0x00FF9A40},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);

        sunCoreRadius = dp(SUN_RADIUS_DP) * SUN_CORE_RADIUS_FACTOR;
        sunCoreShader = new RadialGradient(sunCx, sunCy, sunCoreRadius,
                new int[]{0x99FFD27A, 0x40FFA640, 0x00FF8A30},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);

        // 행성 둘레의 은은한 빛 (대기)
        planetHaloShaders = new Shader[n];
        planetHaloRadius = new float[n];
        for (int i = 0; i < n; i++) {
            float r = iconSizePx(i, sizeMultiplier) / 2f * 1.55f;
            planetHaloRadius[i] = r;
            planetHaloShaders[i] = new RadialGradient(0f, 0f, r,
                    new int[]{0x38CFE3FF, 0x3ACFE3FF, 0x00CFE3FF},
                    new float[]{0f, 0.62f, 1f}, Shader.TileMode.CLAMP);
        }
    }

    private float itemScale() {
        return 0.4f + 0.6f * Math.min(1f, progress);
    }

    private float itemAlpha() {
        return Math.min(1f, progress * 1.6f);
    }

    // ------------------------------------
    // 그리기 (아래층 → 위층 순서)
    //  1) 별  2) 태양 빛무리  3) 소행성대  4) 고리(글로우+중심선)  5) 행성 둘레 빛  → 그 위에 행성(자식 뷰)
    // ------------------------------------

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (progress <= 0f) return;

        int w = getWidth();
        int h = getHeight();
        int n = orbitRadius.length;
        if (n == 0 || ringShaders.length != n) return;

        float fadeIn = Math.min(1f, progress * 1.4f);

        // 1) 배경 별 반짝임
        if (STARS_ENABLED) {
            drawStars(canvas, w, h, fadeIn);
        }

        // 2) 태양 빛무리
        if (SUN_GLOW_ENABLED && sunHaloShader != null) {
            haloPaint.setShader(sunHaloShader);
            haloPaint.setAlpha(Math.round(255 * fadeIn * SUN_GLOW_STRENGTH));
            canvas.drawCircle(sunCx, sunCy, sunHaloRadius, haloPaint);

            haloPaint.setShader(sunCoreShader);
            haloPaint.setAlpha(Math.round(255 * fadeIn * SUN_GLOW_STRENGTH));
            canvas.drawCircle(sunCx, sunCy, sunCoreRadius, haloPaint);
            haloPaint.setShader(null);
        }

        // 3) 소행성대
        if (BELT_ENABLED && n > BELT_AFTER_RING + 1) {
            drawBelt(canvas, w, h, fadeIn);
        }

        // 4) 고리
        for (int i = 0; i < n; i++) {
            float rr = orbitRadius[i] * ringFactor(i);
            arcRect.set(sunCx - rr, sunCy - rr, sunCx + rr, sunCy + rr);

            if (GLOW_ENABLED) {
                float falloff = n > 1 ? 1f - GLOW_OUTER_FADE * i / (n - 1) : 1f;
                glowPaint.setShader(ringShaders[i]);

                for (float[] layer : GLOW_LAYERS) {
                    glowPaint.setStrokeWidth(dp(layer[0]));
                    glowPaint.setAlpha(Math.round(layer[1] * falloff * fadeIn));
                    canvas.drawArc(arcRect, 180f, 180f, false, glowPaint);
                }
            }

            arcPaint.setShader(ringShaders[i]);
            arcPaint.setAlpha(Math.round(ARC_ALPHA * fadeIn));
            canvas.drawArc(arcRect, 180f, 180f, false, arcPaint);
        }
        glowPaint.setShader(null);
        arcPaint.setShader(null);

        // 5) 행성 둘레의 은은한 빛 (행성 그림은 이 위에 자식 뷰로 그려짐)
        if (PLANET_HALO_ENABLED && planetHaloShaders.length == n) {
            float s = itemScale();
            float a = itemAlpha() * PLANET_HALO_ALPHA;

            for (int i = 0; i < n; i++) {
                float f = ringFactor(i);
                float x = sunCx + (itemCx[i] - sunCx) * f;
                float y = sunCy + (itemCy[i] - sunCy) * f;

                canvas.save();
                canvas.translate(x, y);
                canvas.scale(s, s);
                haloPaint.setShader(planetHaloShaders[i]);
                haloPaint.setAlpha(Math.round(255 * a));
                canvas.drawCircle(0f, 0f, planetHaloRadius[i], haloPaint);
                canvas.restore();
            }
            haloPaint.setShader(null);
        }
    }

    private void drawStars(Canvas canvas, int w, int h, float fadeIn) {
        for (int k = 0; k < STAR_COUNT; k++) {
            float x = starX[k] * w;
            float y = starY[k] * h;
            int a = Math.round(starAlpha[k] * fadeIn);

            starPaint.setStyle(Paint.Style.FILL);
            starPaint.setColor(0xFFFFFFFF);
            starPaint.setAlpha(a);
            canvas.drawCircle(x, y, dp(starSize[k]) / 2f, starPaint);

            // 일부는 십자로 반짝이는 별
            if (k < SPARKLE_COUNT) {
                float len = dp(5f + starSize[k] * 3f);
                starPaint.setStyle(Paint.Style.STROKE);
                starPaint.setStrokeWidth(dp(0.7f));
                starPaint.setAlpha(Math.round(a * 0.8f));
                canvas.drawLine(x - len, y, x + len, y, starPaint);
                canvas.drawLine(x, y - len, x, y + len, starPaint);
            }
        }
    }

    private void drawBelt(Canvas canvas, int w, int h, float fadeIn) {
        float rIn = orbitRadius[BELT_AFTER_RING];
        float rOut = orbitRadius[BELT_AFTER_RING + 1];
        float mid = (rIn + rOut) / 2f;
        float half = (rOut - rIn) * 0.32f;
        float f = (ringFactor(BELT_AFTER_RING) + ringFactor(BELT_AFTER_RING + 1)) / 2f;

        for (int k = 0; k < BELT_COUNT; k++) {
            float rad = (mid + beltRadial[k] * half) * f;
            float x = sunCx + rad * beltCos[k];
            float y = sunCy + rad * beltSin[k];

            // 화면 밖 알갱이는 그리지 않음
            if (x < -10f || x > w + 10f || y < -10f || y > h + 10f) continue;

            beltPaint.setColor(beltColor[k]);
            beltPaint.setAlpha(Math.round(beltAlpha[k] * fadeIn));
            canvas.drawCircle(x, y, dp(beltSize[k]) / 2f, beltPaint);
        }
    }

    private static int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    private static int lerpColor(int a, int b, float t) {
        int r = Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * t);
        int g = Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * t);
        int bl = Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t);
        return Color.rgb(r, g, bl);
    }

    // ------------------------------------
    // 열기 / 닫기
    // ------------------------------------

    public void open() {
        if (open) return;
        open = true;

        setVisibility(VISIBLE);
        startWave();
        animateTo(1f, OPEN_MS, new DecelerateInterpolator(1.8f));
    }

    public void close() {
        if (!open) return;
        open = false;

        animateTo(0f, CLOSE_MS, new DecelerateInterpolator());
    }

    private void animateTo(final float to, long durationMs, Interpolator interpolator) {
        if (progressAnimator != null) progressAnimator.cancel();

        ValueAnimator a = ValueAnimator.ofFloat(progress, to);
        a.setDuration(durationMs);
        a.setInterpolator(interpolator);
        a.addUpdateListener(anim -> {
            progress = (float) anim.getAnimatedValue();
            applyState();
        });
        a.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled = false;

            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (cancelled) return;
                if (to == 0f && !open) {
                    stopWave();
                    setVisibility(INVISIBLE);
                }
            }
        });

        progressAnimator = a;
        a.start();
    }

    // 고리 일렁임: 열려 있는 동안만 계속 돈다 (WAVE_ENABLED 가 true 일 때만)
    private void startWave() {
        if (!WAVE_ENABLED) return;
        if (waveAnimator != null && waveAnimator.isRunning()) return;

        waveAnimator = ValueAnimator.ofFloat(0f, (float) (Math.PI * 2));
        waveAnimator.setDuration(WAVE_PERIOD_MS);
        waveAnimator.setInterpolator(new LinearInterpolator());
        waveAnimator.setRepeatCount(ValueAnimator.INFINITE);
        waveAnimator.addUpdateListener(anim -> {
            wavePhase = (float) anim.getAnimatedValue();
            applyState();
        });
        waveAnimator.start();
    }

    private void stopWave() {
        if (waveAnimator != null) {
            waveAnimator.cancel();
            waveAnimator = null;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (progressAnimator != null) progressAnimator.cancel();
        stopWave();
    }

    /** i번째 행성 PNG 한 변 크기(px) = 기본 크기 × 행성별 배율 × 전체 배율 */
    private int iconSizePx(int index, float multiplier) {
        float scale = SIZE_SCALE[index % SIZE_SCALE.length];
        return dp(ICON_DP * scale * multiplier);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    // ============================================================
    // 행성 이미지: 태양 반대쪽 가장자리에 그림자를 입힌다
    // ============================================================

    /**
     * PNG의 투명하지 않은 부분(= 행성 모양)에만 그림자를 입힌다.
     * (dirX, dirY) = 태양에서 행성으로 향하는 단위 벡터 → 그 방향의 가장자리가 어두워진다.
     */
    private static class PlanetImageView extends ImageView {

        private final Paint shadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint rimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float dirX = 0f;
        private float dirY = -1f;
        private boolean hasDirection = false;
        private int shaderW = -1;
        private int shaderH = -1;
        private float shaderDirX = 0f;
        private float shaderDirY = 0f;

        PlanetImageView(Context context) {
            super(context);
            shadePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP));
            rimPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP));
        }

        void setShadowDirection(float dx, float dy) {
            if (hasDirection && dx == dirX && dy == dirY) return;
            dirX = dx;
            dirY = dy;
            hasDirection = true;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (!SHADOW_ENABLED || !hasDirection || getWidth() <= 0 || getHeight() <= 0) {
                super.onDraw(canvas);
                return;
            }

            int w = getWidth();
            int h = getHeight();

            // 행성 그림을 별도 레이어에 그린 뒤, 그 위(불투명한 부분에만)에 그림자 그라디언트를 덮는다
            int saved = canvas.saveLayer(0f, 0f, w, h, null);
            super.onDraw(canvas);

            if (w != shaderW || h != shaderH || dirX != shaderDirX || dirY != shaderDirY) {
                float cx = w / 2f;
                float cy = h / 2f;
                float radius = Math.min(w, h) / 2f;

                // 밝은 쪽(태양 방향) 안쪽에서 시작해서 어두운 쪽(태양 반대) 바깥 가장자리까지
                float x0 = cx - dirX * radius * 0.6f;
                float y0 = cy - dirY * radius * 0.6f;
                float x1 = cx + dirX * radius * 1.0f;
                float y1 = cy + dirY * radius * 1.0f;

                int clear = Color.argb(0, Color.red(SHADOW_COLOR), Color.green(SHADOW_COLOR), Color.blue(SHADOW_COLOR));
                int dark = Color.argb(SHADOW_MAX_ALPHA, Color.red(SHADOW_COLOR), Color.green(SHADOW_COLOR), Color.blue(SHADOW_COLOR));

                // 태양을 마주 보는 쪽 가장자리에 살짝 밝은 빛
                int rimOn = Color.argb(RIM_MAX_ALPHA, Color.red(RIM_COLOR), Color.green(RIM_COLOR), Color.blue(RIM_COLOR));
                int rimOff = Color.argb(0, Color.red(RIM_COLOR), Color.green(RIM_COLOR), Color.blue(RIM_COLOR));
                rimPaint.setShader(new LinearGradient(
                        cx - dirX * radius * 1.0f, cy - dirY * radius * 1.0f,
                        cx - dirX * radius * 0.25f, cy - dirY * radius * 0.25f,
                        new int[]{rimOn, rimOff},
                        new float[]{0f, 1f},
                        Shader.TileMode.CLAMP));

                shadePaint.setShader(new LinearGradient(
                        x0, y0, x1, y1,
                        new int[]{clear, clear, dark},
                        new float[]{0f, SHADOW_START, 1f},
                        Shader.TileMode.CLAMP));

                shaderW = w;
                shaderH = h;
                shaderDirX = dirX;
                shaderDirY = dirY;
            }

            canvas.drawRect(0f, 0f, w, h, rimPaint);
            canvas.drawRect(0f, 0f, w, h, shadePaint);
            canvas.restoreToCount(saved);
        }
    }
}

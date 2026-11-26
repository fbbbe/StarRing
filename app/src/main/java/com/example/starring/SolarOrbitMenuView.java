package com.example.starring;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
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
import java.util.List;

/**
 * 태양 중심 궤도 메뉴.
 *
 * - 태양(하단 중앙)을 중심으로 흰색 얇은 반투명 점선 동심원 궤도 N개를 그립니다.
 * - 행성 N개를 안쪽 궤도부터(= 실제 태양계 순서) 궤도 하나에 하나씩, 고리마다 다른 각도로 흩어 올려놓습니다
 *   (ORBIT_ANGLES_DEG 표 참고 — 태양계 그림처럼 제각각 흩어진 배치).
 * - 열 때 고리와 행성이 같은 애니메이션 하나로 동시에 나타납니다(행성이 고리를 타고 퍼져 나감).
 * - 고리 일렁임 모션은 WAVE_ENABLED 로 켜고 끌 수 있습니다(현재 꺼짐).
 * - 항목이 없는 빈 영역은 터치를 먹지 않아서(뒤의 dim 오버레이가 받음) 바깥 터치로 닫을 수 있습니다.
 */
public class SolarOrbitMenuView extends FrameLayout {

    public interface OnItemSelectedListener {
        void onItemSelected(int index);
    }

    /** 메뉴 항목 하나 */
    public static class Item {
        final int imageRes;      // 행성 PNG
        final String planetName; // 수성, 금성 ... (행성 옆에 표시)
        final String action;     // 역할 (화면에는 안 보이고 접근성 설명에만 사용)

        public Item(int imageRes, String planetName, String action) {
            this.imageRes = imageRes;
            this.planetName = planetName;
            this.action = action;
        }
    }

    // ---- 모양 상수 ----
    private static final float ICON_DP = 56f;            // 행성 크기
    private static final float NAME_SP = 15f;            // 행성 이름 글자 크기
    private static final float ARC_STROKE_DP = 0.8f;     // 고리 두께
    private static final int ARC_ALPHA = 125;            // 고리 투명도 (0~255, 낮을수록 투명)
    private static final float DASH_ON_DP = 4f;          // 점선: 선 길이
    private static final float DASH_OFF_DP = 5f;         // 점선: 빈 길이

    // ---- 배치 상수 ----
    // 안쪽 궤도부터(수성~해왕성) 행성이 놓일 각도. 오른쪽 수평선이 0°, 바로 위가 90°, 왼쪽 수평선이 180°.
    // 여러 화면 크기(폭 320~412dp, 높이 600~915dp)에서 서로 겹치지 않도록 계산해 둔 값.
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
    private static final float EDGE_MARGIN_DP = 36f;      // 화면 좌우 가장자리 여백 (행성이 잘리지 않게)
    private static final float FIRST_ORBIT_DP = 110f;     // 가장 안쪽 궤도 반지름
    private static final float MAX_ORBIT_STEP_DP = 64f;   // 궤도 간격(최대)
    private static final float MIN_ORBIT_STEP_DP = 40f;   // 궤도 간격(최소)
    private static final float TOP_RESERVE_DP = 96f;      // 상단(상태바·은하계 이름) 비워둘 높이
    private static final float NAME_GAP_DP = 3f;          // 행성과 이름 사이 간격

    // ---- 모션 상수 ----
    private static final long OPEN_MS = 380;
    private static final long CLOSE_MS = 240;
    private static final boolean WAVE_ENABLED = false;  // 고리 일렁임 켜기/끄기
    private static final float WAVE_AMP = 0.014f;       // 고리 일렁임 크기 (반지름의 1.4%)
    private static final float WAVE_STEP = 0.55f;       // 고리마다 위상 차이
    private static final long WAVE_PERIOD_MS = 3600;

    private final List<Item> data = new ArrayList<>();
    private final List<View> itemViews = new ArrayList<>();

    private float[] orbitRadius = new float[0];
    private float[] itemCx = new float[0];
    private float[] itemCy = new float[0];

    private float sunCx;
    private float sunCy;
    private int sunFromBottomPx = 0;

    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
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
        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeWidth(getResources().getDisplayMetrics().density * ARC_STROKE_DP);
        arcPaint.setColor(Color.WHITE);
        float density = getResources().getDisplayMetrics().density;
        arcPaint.setPathEffect(new DashPathEffect(
                new float[]{DASH_ON_DP * density, DASH_OFF_DP * density}, 0f));
    }

    // ------------------------------------
    // 설정
    // ------------------------------------

    /** 태양 중심이 화면(이 뷰) 아래쪽에서 몇 px 위에 있는지 */
    public void setSunCenterFromBottom(int px) {
        sunFromBottomPx = px;
        requestLayout();
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
        data.clear();
        data.addAll(items);

        for (int i = 0; i < data.size(); i++) {
            final int index = i;
            View v = createItemView(data.get(i));
            v.setOnClickListener(view -> {
                if (!open) return; // 닫히는 중에는 무시
                if (listener != null) listener.onItemSelected(index);
            });
            addView(v, new LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
            itemViews.add(v);
        }

        orbitRadius = new float[data.size()];
        itemCx = new float[data.size()];
        itemCy = new float[data.size()];

        applyState();
        requestLayout();
    }

    /** 행성 PNG + 아래에 이름. 알약(→ 역할)은 없음 */
    private View createItemView(Item item) {
        LinearLayout box = new LinearLayout(getContext());
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setClickable(true);
        box.setFocusable(true);
        box.setContentDescription(item.planetName + " " + item.action);

        ImageView icon = new ImageView(getContext());
        icon.setImageResource(item.imageRes);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        icon.setContentDescription(null);
        box.addView(icon, new LinearLayout.LayoutParams(dp(ICON_DP), dp(ICON_DP)));

        TextView name = new TextView(getContext());
        name.setText(item.planetName);
        name.setTextSize(NAME_SP);
        name.setTextColor(Color.WHITE);
        name.setShadowLayer(dp(3), 0, dp(1), Color.BLACK);
        name.setSingleLine(true);
        name.getPaint().setFakeBoldText(true);

        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        nameLp.topMargin = dp(NAME_GAP_DP);
        box.addView(name, nameLp);

        return box;
    }

    // ------------------------------------
    // 배치
    // ------------------------------------

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int w = r - l;
        int h = b - t;
        computeGeometry(w, h);

        int iconHalf = dp(ICON_DP) / 2;

        for (int i = 0; i < itemViews.size(); i++) {
            View v = itemViews.get(i);
            int vw = v.getMeasuredWidth();
            int vh = v.getMeasuredHeight();
            int cx = Math.round(itemCx[i]);
            int cy = Math.round(itemCy[i]);

            // (cx, cy) = 행성 PNG의 중심 = 궤도(고리) 위의 점. 이름은 행성 아래로 늘어짐
            int top = cy - iconHalf;
            v.layout(cx - vw / 2, top, cx + vw / 2, top + vh);

            // 크기 애니메이션 기준점 = 행성 중심
            v.setPivotX(vw / 2f);
            v.setPivotY(iconHalf);
        }

        applyState();
    }

    /**
     * 궤도 반지름은 일정 간격으로 늘리고, 행성은 각 궤도 위의 정해진 각도(ORBIT_ANGLES_DEG)에 놓는다.
     * 화면 좌우를 벗어나는 바깥 궤도는 각도를 위쪽으로 당겨서 화면 안에 들어오게 한다.
     */
    private void computeGeometry(int w, int h) {
        int n = itemViews.size();
        if (n == 0) return;

        sunCx = w / 2f;
        sunCy = h - sunFromBottomPx;

        float iconHalf = dp(ICON_DP) / 2f;
        float xMax = w / 2f - dp(EDGE_MARGIN_DP);

        float r0 = dp(FIRST_ORBIT_DP);

        // 화면 높이에 맞춰 궤도 간격 조절 (가장 바깥 궤도의 꼭대기가 상단 여백을 넘지 않게)
        float hMax = sunCy - dp(TOP_RESERVE_DP) - iconHalf;
        float step = n > 1 ? (hMax - r0) / (n - 1) : dp(MAX_ORBIT_STEP_DP);
        step = Math.min(dp(MAX_ORBIT_STEP_DP), step);
        step = Math.max(dp(MIN_ORBIT_STEP_DP), step);

        for (int i = 0; i < n; i++) {
            float radius = r0 + step * i;
            orbitRadius[i] = radius;

            float angleDeg = ORBIT_ANGLES_DEG[i % ORBIT_ANGLES_DEG.length];
            double theta = Math.toRadians(angleDeg);

            float x = (float) (radius * Math.cos(theta));
            if (Math.abs(x) > xMax) {
                x = Math.copySign(xMax, x);
                theta = Math.acos(Math.max(-1f, Math.min(1f, x / radius)));
            }
            float height = (float) (radius * Math.sin(theta));

            itemCx[i] = sunCx + x;
            itemCy[i] = sunCy - height;
        }
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
        float p = progress;
        float itemScale = 0.4f + 0.6f * Math.min(1f, p);
        float itemAlpha = Math.min(1f, p * 1.6f);

        for (int i = 0; i < itemViews.size(); i++) {
            View v = itemViews.get(i);
            float f = ringFactor(i);

            // 행성은 태양에서 뻗은 직선 위에서 고리 반지름과 똑같은 비율로 이동 → 항상 고리 위
            v.setTranslationX((itemCx[i] - sunCx) * (f - 1f));
            v.setTranslationY((itemCy[i] - sunCy) * (f - 1f));
            v.setScaleX(itemScale);
            v.setScaleY(itemScale);
            v.setAlpha(itemAlpha);
        }

        invalidate();
    }

    // ------------------------------------
    // 그리기: 동심원 궤도 (위쪽 반원)
    // ------------------------------------

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (progress <= 0f) return;

        arcPaint.setAlpha(Math.round(ARC_ALPHA * Math.min(1f, progress * 1.4f)));

        for (int i = 0; i < orbitRadius.length; i++) {
            float rr = orbitRadius[i] * ringFactor(i);
            arcRect.set(sunCx - rr, sunCy - rr, sunCx + rr, sunCy + rr);
            canvas.drawArc(arcRect, 180f, 180f, false, arcPaint);
        }
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

    // 고리 일렁임: 열려 있는 동안만 계속 돈다
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

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}


package com.example.starring;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import java.util.List;

import com.example.starring.court.BlackholeEffect;   // na가 추가

import java.util.ArrayList;
import java.util.Random;

public class GalaxyView extends View {

    // 천체 유형
    private static final int CONSTELLATION = 0;
    private static final int SUPERNOVA = 1;
    private static final int WHITE_DWARF = 2;
    private static final int PLANET = 3;

    // 천체 데이터
    private final ArrayList<Star> stars = new ArrayList<>();
    private final ArrayList<Body> reservedBodies = new ArrayList<>();

    private final ArrayList<Body> constellationPositions =
            new ArrayList<>();
    private final ArrayList<Body> supernovaPositions =
            new ArrayList<>();
    private final ArrayList<Body> whiteDwarfPositions =
            new ArrayList<>();
    private final ArrayList<Body> planetPositions =
            new ArrayList<>();

    // 화면에 표시할 천체
    private final ArrayList<Body> visibleConstellations =
            new ArrayList<>();
    private final ArrayList<Body> visibleSpecialBodies =
            new ArrayList<>();
    private final ArrayList<Body> visiblePlanets =
            new ArrayList<>();

    private final Paint imagePaint = new Paint(
            Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG
    );

    // 행성이 둥둥 떠 있는 정도: 위아래 폭(dp), 한 번 오르내리는 시간(ms)
    private static final float PLANET_FLOAT_DP = 5f;
    private static final float PLANET_FLOAT_MS = 4300f;
    // 메인 화면 행성 크기 배율 (1f = 기본, 2f = 2배)
    private static final float PLANET_SIZE_SCALE = 2f;

    private final RectF drawRect = new RectF();
    private final float density;

    // 이미지
    private final Bitmap babyBitmap;
    private final Bitmap bigBitmap;
    private final Bitmap supernovaBitmap;
    private final Bitmap whiteDwarfBitmap;

    private final Bitmap[] constellationBitmaps = new Bitmap[12];
    private final Bitmap[] planetBitmaps = new Bitmap[6];

    // 블랙홀 (우주 재판장에서 내 블랙홀이 진행 중일 때만 표시) - na가 추가
    // 모양/움직임은 court/BlackholeEffect.java 에서 관리
    private final BlackholeEffect blackholeEffect;
    // 위치(은하계 중심 기준 dp)와 크기(dp): 자리를 바꾸려면 이 숫자만 수정 - na가 추가
    private static final float BLACKHOLE_X_DP = 0f;
    private static final float BLACKHOLE_Y_DP = -170f;
    private static final float BLACKHOLE_SIZE_DP = 600f;
    // 블랙홀을 누를 수 있는 반경(dp): 후광을 뺀 실제 모습 기준
    private static final float BLACKHOLE_TAP_DP = 110f;

    private static final int[] CONSTELLATION_RESOURCES = {
            R.drawable.constellation_01,
            R.drawable.constellation_02,
            R.drawable.constellation_03,
            R.drawable.constellation_04,
            R.drawable.constellation_05,
            R.drawable.constellation_06,
            R.drawable.constellation_07,
            R.drawable.constellation_08,
            R.drawable.constellation_09,
            R.drawable.constellation_10,
            R.drawable.constellation_11,
            R.drawable.constellation_12
    };

    private static final int[] PLANET_RESOURCES = {
            R.drawable.planet_01,
            R.drawable.planet_02,
            R.drawable.planet_03,
            R.drawable.planet_04,
            R.drawable.planet_05,
            R.drawable.planet_06
    };

    // 최소 간격 (dp)
    private static final float CONSTELLATION_MIN_GAP = 70f;
    private static final float SUPERNOVA_MIN_GAP = 100f;
    private static final float WHITE_DWARF_MIN_GAP = 80f;
    private static final float PLANET_MIN_GAP = 110f;

    // 처음 화면(핸드폰 범위) 안에 행성을 놓기 위한 영역 (가운데 기준 절반 크기, dp)
    private static final float PLANET_AREA_HALF_W = 165f;
    private static final float PLANET_AREA_HALF_H = 240f;
    // 행성이 다른 천체와 떨어져야 하는 최소 간격(dp)
    private static final float PLANET_GAP = 20f;

    // 블랙홀 주변에는 행성을 놓지 않음 (블랙홀 중심에서의 거리, dp)
    private static final float BLACKHOLE_AVOID_DP = 190f;
    // 행성 영역의 세로 중심을 아래로 내림 (dp)
    private static final float PLANET_AREA_CENTER_Y = 70f;

    // 생성 가능한 최대 개수
    private static final int MAX_SPECIAL_BODIES = 20;
    private static final int MAX_PLANETS = 24;

    // 카메라
    private float zoom = 1f;
    private float offsetX = 0f;
    private float offsetY = 0f;

    private static final float MIN_ZOOM = 0.3f;
    private static final float MAX_ZOOM = 4f;

    private final ScaleGestureDetector scaleDetector;
    private float lastX;
    private float lastY;
    private boolean hadMultiTouch = false;

    // 블랙홀 터치 처리 - na가 추가
    private float downX;
    private float downY;

    public interface OnBlackholeClickListener {
        void onBlackholeClick();
    }

    private OnBlackholeClickListener blackholeClickListener;

    // 현재 천체 생성 상태
    private int currentDays = -1;
    private int currentMonths = -1;
    private int currentConstellationMask = -1;

    private int supernovaCount = 0;
    private int whiteDwarfCount = 0;
    private int planetCount = 0;

    // 애기별 / 큰 별
    private static class Star {
        final float x;
        final float y;
        final float size;
        final boolean big;

        Star(float x, float y, float size, boolean big) {
            this.x = x;
            this.y = y;
            this.size = size;
            this.big = big;
        }
    }

    // Firestore에 저장된 실제 행성 문서 정보
    private final ArrayList<String> planetDocumentIds =
            new ArrayList<>();

    private final ArrayList<String> planetAssets =
            new ArrayList<>();

    public interface OnPlanetClickListener {
        void onPlanetClick(String planetId);
    }

    private OnPlanetClickListener planetClickListener;

    public void setOnPlanetClickListener(
            OnPlanetClickListener listener) {
        planetClickListener = listener;
    }

    // Firebase에서 받아온 행성 목록을 화면에 반영
    public void setPlanetDocuments(
            List<String> ids,
            List<String> assets) {

        planetDocumentIds.clear();
        planetAssets.clear();

        int count = Math.min(
                planetPositions.size(),
                ids.size()
        );

        for (int i = 0; i < count; i++) {
            planetDocumentIds.add(ids.get(i));

            String asset = i < assets.size()
                    ? assets.get(i)
                    : "planet_01";

            planetAssets.add(asset);
        }

        setPlanetCount(count);
        invalidate();
    }


    // 별자리 / 초신성 / 백색왜성 / 행성
    private static class Body {
        final int type;
        final int id;

        final float x;
        final float y;
        final float width;
        final float height;
        final float minGap;

        Body(int type, int id,
             float x, float y,
             float width, float height,
             float minGap) {

            this.type = type;
            this.id = id;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.minGap = minGap;
        }
    }

    public GalaxyView(Context context) {
        super(context);

        density = getResources()
                .getDisplayMetrics().density;

        babyBitmap = BitmapFactory.decodeResource(
                getResources(), R.drawable.star_baby
        );

        bigBitmap = BitmapFactory.decodeResource(
                getResources(), R.drawable.star_big
        );

        supernovaBitmap = BitmapFactory.decodeResource(
                getResources(), R.drawable.star_supernova
        );

        whiteDwarfBitmap = BitmapFactory.decodeResource(
                getResources(), R.drawable.star_white_dwarf
        );

        // 별자리 이미지
        BitmapFactory.Options options =
                new BitmapFactory.Options();

        options.inSampleSize = 2;

        for (int i = 0; i < 12; i++) {
            constellationBitmaps[i] =
                    BitmapFactory.decodeResource(
                            getResources(),
                            CONSTELLATION_RESOURCES[i],
                            options
                    );
        }

        // 행성 이미지
        for (int i = 0; i < PLANET_RESOURCES.length; i++) {
            planetBitmaps[i] =
                    BitmapFactory.decodeResource(
                            getResources(),
                            PLANET_RESOURCES[i]
                    );
        }

        // 블랙홀 연출 - na가 추가
        blackholeEffect = new BlackholeEffect(this);

        // 순서가 중요함.
        // 기존 천체 위치를 먼저 확정한 뒤 행성을 배치
        createConstellationPositions();
        createSpecialPositions();
        createPlanetPositions();

        setClickable(true);

        scaleDetector = new ScaleGestureDetector(
                context,
                new ScaleGestureDetector
                        .SimpleOnScaleGestureListener() {

                    @Override
                    public boolean onScale(
                            ScaleGestureDetector detector) {

                        float oldZoom = zoom;

                        zoom *= detector.getScaleFactor();

                        zoom = Math.max(
                                MIN_ZOOM,
                                Math.min(zoom, MAX_ZOOM)
                        );

                        float ratio = zoom / oldZoom;

                        float fx = detector.getFocusX()
                                - getWidth() / 2f;

                        float fy = detector.getFocusY()
                                - getHeight() / 2f;

                        offsetX = fx
                                - (fx - offsetX) * ratio;

                        offsetY = fy
                                - (fy - offsetY) * ratio;

                        limitCamera();
                        invalidate();
                        return true;
                    }
                }
        );
    }

    // -----------------------------------
    // 1. 애기별 / 큰 별
    // -----------------------------------

    public void setStarCounts(int days, int months) {

        days = Math.max(0, days);
        months = Math.max(0, months);

        if (currentDays == days &&
                currentMonths == months) {
            return;
        }

        currentDays = days;
        currentMonths = months;

        stars.clear();

        for (int i = 1; i <= days; i++) {
            stars.add(createStar(i, false));
        }

        for (int i = 1; i <= months; i++) {
            stars.add(createStar(i, true));
        }

        limitCamera();
        invalidate();
    }

    private Star createStar(int index, boolean big) {

        long seed = 913701L
                + (long) index * 104729L
                + (big ? 500000L : 0L);

        Random random = new Random(seed);

        int order = big ? index * 30 : index;

        float maxRadius = 50f
                + 28f * (float) Math.sqrt(order);

        float x;
        float y;

        boolean clustered = order > 20
                && random.nextFloat() < 0.38f;

        if (clustered) {

            float[][] centers = {
                    {-170f, -140f},
                    {190f, -110f},
                    {-130f, 210f},
                    {180f, 200f}
            };

            int cluster =
                    random.nextInt(centers.length);

            x = centers[cluster][0]
                    + (float) random.nextGaussian() * 90f;

            y = centers[cluster][1]
                    + (float) random.nextGaussian() * 110f;

        } else {

            double angle =
                    random.nextDouble() * Math.PI * 2;

            float distance = maxRadius
                    * (float) Math.sqrt(random.nextDouble());

            x = (float) (Math.cos(angle) * distance);
            y = (float) (Math.sin(angle) * distance);
        }

        float size = big
                ? 13f + random.nextFloat() * 20f
                : 5f + random.nextFloat() * 20f;

        return new Star(x, y, size, big);
    }

    // -----------------------------------
    // 2. 천체 공통 배치
    // -----------------------------------

    // 행성 전용 배치: 처음 화면 안의 직사각형 영역에 놓음
    private Body placePlanet(int id, float width, float height, Random random) {

        float scale = 1f;

        while (true) {

            // 행성이 화면 밖으로 삐져나오지 않도록 행성 크기만큼 안쪽에서 고름
            float rangeX = Math.max(
                    10f, PLANET_AREA_HALF_W * scale - width / 2f);

            float rangeY = Math.max(
                    10f, PLANET_AREA_HALF_H * scale - height / 2f);

            for (int attempt = 0; attempt < 400; attempt++) {

                float x = (random.nextFloat() * 2f - 1f) * rangeX;
                float y = PLANET_AREA_CENTER_Y
                        + (random.nextFloat() * 2f - 1f) * rangeY;

                if (isFarEnoughForPlanet(x, y, width, height)) {

                    Body body = new Body(
                            PLANET, id, x, y, width, height, PLANET_MIN_GAP
                    );

                    reservedBodies.add(body);
                    return body;
                }
            }

            // 자리가 없으면 영역을 조금씩 넓힘 (행성이 많아졌을 때)
            scale += 0.25f;
        }
    }

    private boolean isFarEnoughForPlanet(
            float x, float y, float width, float height) {

        // 블랙홀과 너무 가까우면 제외 (행성 크기의 절반만큼 더 멀리)
        double toBlackhole = Math.hypot(
                x - BLACKHOLE_X_DP,
                y - BLACKHOLE_Y_DP
        );

        if (toBlackhole < BLACKHOLE_AVOID_DP + Math.max(width, height) / 2f) {
            return false;
        }

        for (Body other : reservedBodies) {

            // 아직 화면에 안 나오는 초신성/백색왜성(3번째 이후)은 무시
            if ((other.type == SUPERNOVA || other.type == WHITE_DWARF)
                    && other.id > 2) {
                continue;
            }

            float dx = Math.max(
                    0f,
                    Math.abs(x - other.x) - (width + other.width) / 2f
            );

            float dy = Math.max(
                    0f,
                    Math.abs(y - other.y) - (height + other.height) / 2f
            );

            if (Math.hypot(dx, dy) < PLANET_GAP) {
                return false;
            }
        }

        return true;
    }


    private Body placeBody(
            int type, int id,
            float width, float height,
            float minGap,
            Random random) {

        float radius = 550f;

        while (true) {

            for (int attempt = 0; attempt < 300; attempt++) {

                double angle =
                        random.nextDouble() * Math.PI * 2;

                float distance = radius
                        * (float) Math.sqrt(random.nextDouble());

                float x = (float)
                        (Math.cos(angle) * distance);

                float y = (float)
                        (Math.sin(angle) * distance);

                if (isFarEnough(
                        x, y, width, height, minGap)) {

                    Body body = new Body(
                            type, id,
                            x, y, width, height, minGap
                    );

                    reservedBodies.add(body);
                    return body;
                }
            }

            radius += 200f;
        }
    }

    // 이미지 표시 영역 사이 최소 거리 검사
    private boolean isFarEnough(
            float x, float y,
            float width, float height,
            float minGap) {

        for (Body other : reservedBodies) {

            float dx = Math.max(
                    0f,
                    Math.abs(x - other.x)
                            - (width + other.width) / 2f
            );

            float dy = Math.max(
                    0f,
                    Math.abs(y - other.y)
                            - (height + other.height) / 2f
            );

            double distance = Math.hypot(dx, dy);

            float requiredGap = Math.max(
                    minGap,
                    other.minGap
            );

            if (distance < requiredGap) {
                return false;
            }
        }

        return true;
    }

    // -----------------------------------
    // 3. 별자리
    // -----------------------------------

    private void createConstellationPositions() {

        Random random = new Random(20261119L);

        for (int month = 1; month <= 12; month++) {

            Bitmap bitmap =
                    constellationBitmaps[month - 1];

            if (bitmap == null) {
                continue;
            }

            float width = 95f + (month % 3) * 10f;

            float height = width
                    * bitmap.getHeight()
                    / (float) bitmap.getWidth();

            Body body = placeBody(
                    CONSTELLATION,
                    month,
                    width,
                    height,
                    CONSTELLATION_MIN_GAP,
                    random
            );

            constellationPositions.add(body);
        }
    }

    public void setConstellationMask(int mask) {

        if (currentConstellationMask == mask) {
            return;
        }

        currentConstellationMask = mask;
        visibleConstellations.clear();

        for (Body body : constellationPositions) {

            int bit = 1 << (body.id - 1);

            if ((mask & bit) != 0) {
                visibleConstellations.add(body);
            }
        }

        limitCamera();
        invalidate();
    }

    // -----------------------------------
    // 4. 초신성 / 백색왜성
    // -----------------------------------

    private void createSpecialPositions() {

        Random supernovaRandom = new Random(842113L);
        Random whiteDwarfRandom = new Random(934871L);

        for (int i = 1; i <= MAX_SPECIAL_BODIES; i++) {

            float width = 40f
                    + supernovaRandom.nextFloat() * 20f;

            float height = width
                    * supernovaBitmap.getHeight()
                    / (float) supernovaBitmap.getWidth();

            Body body = placeBody(
                    SUPERNOVA,
                    i,
                    width,
                    height,
                    SUPERNOVA_MIN_GAP,
                    supernovaRandom
            );

            supernovaPositions.add(body);
        }

        for (int i = 1; i <= MAX_SPECIAL_BODIES; i++) {

            float width = 20f
                    + whiteDwarfRandom.nextFloat() * 12f;

            float height = width
                    * whiteDwarfBitmap.getHeight()
                    / (float) whiteDwarfBitmap.getWidth();

            Body body = placeBody(
                    WHITE_DWARF,
                    i,
                    width,
                    height,
                    WHITE_DWARF_MIN_GAP,
                    whiteDwarfRandom
            );

            whiteDwarfPositions.add(body);
        }
    }

    public void setSpecialBodyCounts(
            int supernova, int whiteDwarf) {

        supernova = Math.max(
                0, Math.min(supernova, MAX_SPECIAL_BODIES)
        );

        whiteDwarf = Math.max(
                0, Math.min(whiteDwarf, MAX_SPECIAL_BODIES)
        );

        if (supernovaCount == supernova &&
                whiteDwarfCount == whiteDwarf) {
            return;
        }

        supernovaCount = supernova;
        whiteDwarfCount = whiteDwarf;

        visibleSpecialBodies.clear();

        for (int i = 0; i < supernova; i++) {
            visibleSpecialBodies.add(
                    supernovaPositions.get(i)
            );
        }

        for (int i = 0; i < whiteDwarf; i++) {
            visibleSpecialBodies.add(
                    whiteDwarfPositions.get(i)
            );
        }

        limitCamera();
        invalidate();
    }

    // -----------------------------------
    // 5. 행성 생성
    // -----------------------------------

    private void createPlanetPositions() {

        // 고정 시드: 앱 재실행 후에도 동일한 배치
        Random random = new Random(552011L);

        // 기존 천체의 위치는 변경하지 않고
        // 빈 공간에 행성을 추가
        for (int i = 1; i <= MAX_PLANETS; i++) {

            Bitmap bitmap = planetBitmaps[
                    (i - 1) % planetBitmaps.length
                    ];

            if (bitmap == null) {
                continue;
            }

            // 행성 크기: 기본 35~75dp 에 배율 적용 (2배 = 70~150dp)
            float width = (35f + random.nextFloat() * 40f) * PLANET_SIZE_SCALE;

            // PNG 원본 비율 유지
            float height = width
                    * bitmap.getHeight()
                    / (float) bitmap.getWidth();

            Body body = placePlanet(i, width, height, random);

            planetPositions.add(body);
        }
    }

    // 실제 화면에 표시할 행성 개수
    public void setPlanetCount(int count) {

        count = Math.max(
                0,
                Math.min(count, MAX_PLANETS)
        );

        if (planetCount == count) {
            return;
        }

        planetCount = count;
        visiblePlanets.clear();

        for (int i = 0; i < count; i++) {
            visiblePlanets.add(planetPositions.get(i));
        }

        limitCamera();
        invalidate();
    }

    // -----------------------------------
    // 6. 화면에 천체 그리기
    // -----------------------------------

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // 블랙홀이 있을 때 가끔 화면 전체가 흔들림 (평소엔 0)
        canvas.save();
        canvas.translate(blackholeEffect.shakeX(), blackholeEffect.shakeY());

        drawStars(canvas);
        drawBodies(canvas, visibleConstellations);
        drawBodies(canvas, visibleSpecialBodies);
        drawBodies(canvas, visiblePlanets);
        drawBlackhole(canvas);   // na가 추가

        canvas.restore();

        // 행성이 떠다니도록 계속 다시 그림 (행성이 있을 때만)
        if (!visiblePlanets.isEmpty()) {
            postInvalidateDelayed(33L);
        }

        // 흔들리는 동안은 부드럽게 보이도록 프레임마다 다시 그림
        if (blackholeEffect.isShaking()) {
            postInvalidateOnAnimation();
        }
    }

    // 우주 재판장 연동: 진행 중인 내 블랙홀이 있으면 표시 - na가 추가
    // (없다가 생기는 순간에만 등장 연출이 나옴)
    public void setBlackholeVisible(boolean visible) {
        blackholeEffect.setVisible(visible);
    }

    // 블랙홀 아래 타이머 글자 (null 이면 숨김), urgent = 붉게 깜빡임 - na가 추가
    public void setBlackholeLabel(String text, boolean urgent) {
        blackholeEffect.setLabel(text, urgent);
    }

    public void setOnBlackholeClickListener(OnBlackholeClickListener listener) {
        blackholeClickListener = listener;
    }

    // 드래그가 아닌 짧은 탭이면서 블랙홀 이미지 위를 눌렀는지
    private boolean isTapOnBlackhole(float upX, float upY) {

        if (!blackholeEffect.isVisible() || blackholeClickListener == null) {
            return false;
        }

        // 10dp 이상 움직였으면 드래그로 보고 제외
        float slop = 10f * density;
        if (Math.abs(upX - downX) > slop || Math.abs(upY - downY) > slop) {
            return false;
        }

        float cx = getWidth() / 2f + offsetX
                + BLACKHOLE_X_DP * density * zoom;
        float cy = getHeight() / 2f + offsetY
                + BLACKHOLE_Y_DP * density * zoom;

        // 이미지가 작아져도 누르기 쉽게 최소 24dp 반경은 보장
        float radius = Math.max(
                BLACKHOLE_TAP_DP * density * zoom,
                24f * density);

        float dx = upX - cx;
        float dy = upY - cy;
        return dx * dx + dy * dy <= radius * radius;
    }

    private void drawBlackhole(Canvas canvas) {
        if (!blackholeEffect.isVisible()) { return; }
        float x = getWidth() / 2f + offsetX
                + BLACKHOLE_X_DP * density * zoom;
        float y = getHeight() / 2f + offsetY
                + BLACKHOLE_Y_DP * density * zoom;
        float half = BLACKHOLE_SIZE_DP * density * zoom / 2f;

        // 후광과 아래 타이머까지 감안해 넉넉하게 화면 밖 판정
        float margin = half * 1.8f + 40f * density;
        if (x + margin < 0 || x - margin > getWidth()
                || y + margin < 0 || y - margin > getHeight()) {
            return;
        }

        blackholeEffect.draw(canvas, x, y, half);
    }

    // 화면에 보일 때만 블랙홀 애니메이션 동작 (배터리 절약) - na가 추가
    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        blackholeEffect.setRunning(getWindowVisibility() == View.VISIBLE);
    }

    @Override
    protected void onDetachedFromWindow() {
        blackholeEffect.setRunning(false);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        blackholeEffect.setRunning(visibility == View.VISIBLE && isAttachedToWindow());
    }

    private void drawStars(Canvas canvas) {

        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;

        for (Star star : stars) {

            float x = centerX + offsetX
                    + star.x * density * zoom;

            float y = centerY + offsetY
                    + star.y * density * zoom;



            // 애기별과 큰 별은 크기 고정
            float size = star.size * density;
            float half = size / 2f;

            if (x + half < 0 ||
                    x - half > getWidth() ||
                    y + half < 0 ||
                    y - half > getHeight()) {
                continue;
            }

            Bitmap bitmap = star.big
                    ? bigBitmap : babyBitmap;

            if (bitmap == null) {
                continue;
            }

            drawRect.set(
                    x - half,
                    y - half,
                    x + half,
                    y + half
            );

            canvas.drawBitmap(
                    bitmap, null, drawRect, imagePaint
            );
        }
    }

    private void drawBodies(
            Canvas canvas, ArrayList<Body> bodies) {

        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;

        for (Body body : bodies) {

            Bitmap bitmap = getBodyBitmap(body);

            if (bitmap == null) {
                continue;
            }

            float x = centerX + offsetX
                    + body.x * density * zoom;

            float y = centerY + offsetY
                    + body.y * density * zoom;

            if (body.type == PLANET) {
                y += (float) Math.sin(2.0 * Math.PI
                        * (android.os.SystemClock.uptimeMillis() / PLANET_FLOAT_MS
                        + body.id * 0.37)) * PLANET_FLOAT_DP * density * zoom;
            }

            // 별자리·초신성·백색왜성·행성은
            // 위치와 크기 모두 줌 적용
            float width = body.width * density * zoom;
            float height = body.height * density * zoom;

            if (body.type == PLANET && bitmap.getWidth() > 0) {
                height = width * bitmap.getHeight()
                        / (float) bitmap.getWidth();
            }

            float halfW = width / 2f;
            float halfH = height / 2f;

            if (x + halfW < 0 ||
                    x - halfW > getWidth() ||
                    y + halfH < 0 ||
                    y - halfH > getHeight()) {
                continue;
            }

            drawRect.set(
                    x - halfW,
                    y - halfH,
                    x + halfW,
                    y + halfH
            );

            canvas.drawBitmap(
                    bitmap, null, drawRect, imagePaint
            );
        }
    }


    private String findTappedPlanet(float touchX, float touchY) {

        float slop = 10f * density;

        // 드래그를 클릭으로 인식하지 않음
        if (Math.abs(touchX - downX) > slop
                || Math.abs(touchY - downY) > slop) {
            return null;
        }

        // 화면에서 마지막에 그린 행성부터 확인
        for (int i = visiblePlanets.size() - 1; i >= 0; i--) {

            Body body = visiblePlanets.get(i);
            int slot = body.id - 1;

            if (slot < 0
                    || slot >= planetDocumentIds.size()) {
                continue;
            }

            Bitmap image = getBodyBitmap(body);

            if (image == null || image.getWidth() == 0) {
                continue;
            }

            float centerX = getWidth() / 2f + offsetX
                    + body.x * density * zoom;

            float centerY = getHeight() / 2f + offsetY
                    + body.y * density * zoom;

            float imageWidth =
                    body.width * density * zoom;

            float imageHeight = imageWidth
                    * image.getHeight()
                    / (float) image.getWidth();

            // 작은 행성도 터치할 수 있도록 최소 영역 확보
            float halfW = Math.max(
                    imageWidth / 2f,
                    24f * density
            );

            float halfH = Math.max(
                    imageHeight / 2f,
                    24f * density
            );

            if (Math.abs(touchX - centerX) <= halfW
                    && Math.abs(touchY - centerY) <= halfH) {

                return planetDocumentIds.get(slot);
            }
        }

        return null;
    }

    private Bitmap getBodyBitmap(Body body) {

        if (body.type == CONSTELLATION) {
            // id = 월(1~12)
            return constellationBitmaps[body.id - 1];
        }

        if (body.type == SUPERNOVA) {
            return supernovaBitmap;
        }

        if (body.type == WHITE_DWARF) {
            return whiteDwarfBitmap;
        }

        if (body.type == PLANET) {

            int slot = body.id - 1;
            int index = slot % planetBitmaps.length;

            // 행성 문서의 planetAsset("planet_03")대로 모양을 고름
            if (slot >= 0 && slot < planetAssets.size()) {

                String asset = planetAssets.get(slot);

                if (asset != null && asset.startsWith("planet_")) {
                    try {
                        int n = Integer.parseInt(asset.substring(7));
                        if (n >= 1 && n <= planetBitmaps.length) {
                            index = n - 1;
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }

            return planetBitmaps[index];
        }

        return null;
    }

    // -----------------------------------
    // 7. 카메라 이동 범위
    // -----------------------------------

    private void limitCamera() {

        if (getWidth() == 0 || getHeight() == 0) {
            return;
        }

        int days = Math.max(0, currentDays);
        int months = Math.max(0, currentMonths);

        int order = Math.max(days, months * 30);

        float worldRadius = 50f
                + 28f * (float) Math.sqrt(order);

        for (Body body : visibleConstellations) {
            worldRadius = Math.max(
                    worldRadius, getBodyExtent(body)
            );
        }

        for (Body body : visibleSpecialBodies) {
            worldRadius = Math.max(
                    worldRadius, getBodyExtent(body)
            );
        }

        // 행성이 멀리 생성되어도 이동할 수 있도록
        for (Body body : visiblePlanets) {
            worldRadius = Math.max(
                    worldRadius, getBodyExtent(body)
            );
        }

        float radiusPx = worldRadius * density * zoom;

        float maxX = Math.max(
                100f * density,
                radiusPx + getWidth() * 0.25f
        );

        float maxY = Math.max(
                100f * density,
                radiusPx + getHeight() * 0.25f
        );

        offsetX = Math.max(
                -maxX,
                Math.min(offsetX, maxX)
        );

        offsetY = Math.max(
                -maxY,
                Math.min(offsetY, maxY)
        );
    }

    private float getBodyExtent(Body body) {

        float extentX = Math.abs(body.x)
                + body.width / 2f;

        float extentY = Math.abs(body.y)
                + body.height / 2f;

        return Math.max(extentX, extentY) + 50f;
    }

    @Override
    protected void onSizeChanged(
            int w, int h, int oldw, int oldh) {

        super.onSizeChanged(w, h, oldw, oldh);
        limitCamera();
    }

    // -----------------------------------
    // 8. 드래그 및 핀치 줌
    // -----------------------------------

    @Override
    public boolean onTouchEvent(MotionEvent event) {

        scaleDetector.onTouchEvent(event);

        switch (event.getActionMasked()) {

            case MotionEvent.ACTION_DOWN:

                lastX = event.getX();
                lastY = event.getY();
                downX = lastX;
                downY = lastY;
                hadMultiTouch = false;
                return true;

            case MotionEvent.ACTION_POINTER_DOWN:

                hadMultiTouch = true;
                return true;

            case MotionEvent.ACTION_MOVE:

                if (!hadMultiTouch
                        && event.getPointerCount() == 1
                        && !scaleDetector.isInProgress()) {

                    float x = event.getX();
                    float y = event.getY();

                    offsetX += x - lastX;
                    offsetY += y - lastY;

                    lastX = x;
                    lastY = y;

                    limitCamera();
                    invalidate();
                }
                return true;

            case MotionEvent.ACTION_POINTER_UP:
                return true;


            case MotionEvent.ACTION_UP:

                if (!hadMultiTouch) {

                    float x = event.getX();
                    float y = event.getY();

                    // 행성을 먼저 확인하고, 행성이 아니면 블랙홀 처리
                    String planetId = findTappedPlanet(x, y);

                    if (planetId != null && planetClickListener != null) {

                        planetClickListener.onPlanetClick(planetId);

                    } else if (isTapOnBlackhole(x, y)) {

                        blackholeClickListener.onBlackholeClick();
                    }

                    performClick();
                }

                hadMultiTouch = false;
                return true;


            case MotionEvent.ACTION_CANCEL:

                hadMultiTouch = false;
                return true;
        }

        return true;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }
}


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

import java.util.ArrayList;
import java.util.Random;

public class GalaxyView extends View {

    private final ArrayList<Star> stars =
            new ArrayList<>();

    private final ArrayList<Constellation> constellations =
            new ArrayList<>();

    private final Paint imagePaint = new Paint(
            Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG
    );

    private final RectF drawRect = new RectF();

    private final Bitmap babyBitmap;
    private final Bitmap bigBitmap;

    // 월별 별자리 PNG
    private final Bitmap[] constellationBitmaps =
            new Bitmap[12];

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

    private final float density;

    // 카메라 설정
    private float zoom = 1.0f;
    private float offsetX = 0f;
    private float offsetY = 0f;

    private static final float MIN_ZOOM = 0.3f;
    private static final float MAX_ZOOM = 4.0f;

    // 터치
    private final ScaleGestureDetector scaleDetector;
    private float lastX;
    private float lastY;
    private boolean hadMultiTouch = false;

    // 생성 상태
    private int currentDays = -1;
    private int currentMonths = -1;
    private int currentConstellationMask = -1;

    // 애기별 및 큰 별
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

    // 12개 별자리의 고정 배치 정보
    private final ArrayList<Constellation> constellationPositions =
            new ArrayList<>();

    // 별자리 이미지 사이의 최소 간격 (dp)
    private static final float CONSTELLATION_MIN_GAP = 150f;

    // 별자리 초기 생성 범위 (dp)
    private static final float CONSTELLATION_RADIUS = 550f;



    // 별자리
    private static class Constellation {

        final int month;
        final float x;
        final float y;
        final float width;

        Constellation(
                int month, float x, float y, float width) {

            this.month = month;
            this.x = x;
            this.y = y;
            this.width = width;
        }
    }

    public GalaxyView(Context context) {
        super(context);

        density = getResources()
                .getDisplayMetrics().density;

        babyBitmap = BitmapFactory.decodeResource(
                getResources(),
                R.drawable.star_baby
        );

        bigBitmap = BitmapFactory.decodeResource(
                getResources(),
                R.drawable.star_big
        );

        // 별자리 12종 이미지 불러오기
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
            createConstellationPositions();
        }


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

    // 애기별 및 큰 별 갱신
    public void setStarCounts(int days, int months) {

        days = Math.max(0, days);
        months = Math.max(0, months);

        if (currentDays == days
                && currentMonths == months) {
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

    // 기존 불규칙한 별 배치 유지
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

        boolean clustered =
                order > 20 && random.nextFloat() < 0.38f;

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
                    * (float) Math.sqrt(
                    random.nextDouble()
            );

            x = (float)
                    (Math.cos(angle) * distance);

            y = (float)
                    (Math.sin(angle) * distance);
        }

        float size;

        if (big) {
            size = 13f + random.nextFloat() * 9f;
        } else {
            size = 5f + random.nextFloat() * 3f;
        }

        return new Star(x, y, size, big);
    }


    public void setConstellationMask(int mask) {

        if (currentConstellationMask == mask) {
            return;
        }

        currentConstellationMask = mask;

        constellations.clear();

        // 미리 생성한 12개 위치 중
        // 실제로 생성된 월의 별자리만 표시
        for (Constellation constellation : constellationPositions) {

            int bit = 1 << (constellation.month - 1);

            if ((mask & bit) != 0) {
                constellations.add(constellation);
            }
        }

        limitCamera();
        invalidate();
    }


    // 별자리 12개의 위치를 무작위로 생성
    private void createConstellationPositions() {

        constellationPositions.clear();

        // 고정 시드를 사용하므로 앱 재실행 후에도
        // 같은 월의 별자리는 같은 위치에 생성됨
        Random random = new Random(20261119L);

        for (int month = 1; month <= 12; month++) {

            Bitmap bitmap = constellationBitmaps[month - 1];

            if (bitmap == null) {
                continue;
            }

            // 별자리 크기
            float width = 95f + (month % 3) * 10f;

            float height = width
                    * bitmap.getHeight()
                    / (float) bitmap.getWidth();

            float radius = CONSTELLATION_RADIUS;

            boolean placed = false;

            // 배치 공간이 부족하면 범위를 확장
            while (!placed) {

                // 현재 범위에서 최대 300번 시도
                for (int attempt = 0; attempt < 300; attempt++) {

                    double angle =
                            random.nextDouble() * Math.PI * 2;

                    float distance = radius
                            * (float) Math.sqrt(
                            random.nextDouble()
                    );

                    float x = (float)
                            (Math.cos(angle) * distance);

                    float y = (float)
                            (Math.sin(angle) * distance);

                    // 기존 별자리와 최소 거리 검사
                    if (isFarEnough(x, y, width, height)) {

                        constellationPositions.add(
                                new Constellation(
                                        month, x, y, width
                                )
                        );

                        placed = true;
                        break;
                    }
                }

                // 300번 시도해도 공간이 없으면 확장
                if (!placed) {
                    radius += 200f;
                }
            }
        }
    }


    // 다른 별자리와 최소 거리 검사
    private boolean isFarEnough(
            float x, float y,
            float width, float height) {

        for (Constellation other : constellationPositions) {

            Bitmap bitmap =
                    constellationBitmaps[other.month - 1];

            float otherWidth = other.width;

            float otherHeight = otherWidth
                    * bitmap.getHeight()
                    / (float) bitmap.getWidth();

            // 두 이미지 영역 사이의 X, Y 거리
            float dx = Math.max(
                    0f,
                    Math.abs(x - other.x)
                            - (width + otherWidth) / 2f
            );

            float dy = Math.max(
                    0f,
                    Math.abs(y - other.y)
                            - (height + otherHeight) / 2f
            );

            // 이미지 영역 사이의 실제 최단 거리
            double distance = Math.hypot(dx, dy);

            if (distance < CONSTELLATION_MIN_GAP) {
                return false;
            }
        }

        return true;
    }

    // 애기별 및 큰 별 그리기
    private void drawStars(Canvas canvas) {

        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;

        for (Star star : stars) {

            float x = centerX + offsetX
                    + star.x * density * zoom;

            float y = centerY + offsetY
                    + star.y * density * zoom;

            // 별 크기는 줌의 영향 없음
            float size = star.size * density;
            float half = size / 2f;

            if (x + half < 0 ||
                    x - half > getWidth() ||
                    y + half < 0 ||
                    y - half > getHeight()) {
                continue;
            }

            Bitmap bitmap = star.big
                    ? bigBitmap
                    : babyBitmap;

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
                    bitmap,
                    null,
                    drawRect,
                    imagePaint
            );
        }
    }

    // 별자리 이미지 그리기
    private void drawConstellations(Canvas canvas) {

        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;

        for (Constellation constellation : constellations) {

            Bitmap bitmap = constellationBitmaps[
                    constellation.month - 1
                    ];

            if (bitmap == null) {
                continue;
            }

            // 별자리의 위치는 카메라 이동과 줌 적용
            float x = centerX + offsetX
                    + constellation.x * density * zoom;

            float y = centerY + offsetY
                    + constellation.y * density * zoom;

            // 별자리는 줌에 따라 이미지 크기도 변화
            float width =
                    constellation.width * density * zoom;

            // 원본 이미지 비율 유지
            float height = width
                    * bitmap.getHeight()
                    / (float) bitmap.getWidth();

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
                    bitmap,
                    null,
                    drawRect,
                    imagePaint
            );
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // 먼저 별을 그리고 그 위에 별자리 표시
        drawStars(canvas);
        drawConstellations(canvas);
    }

    // 카메라 이동 범위 제한
    private void limitCamera() {

        if (getWidth() == 0 || getHeight() == 0) {
            return;
        }

        int days = Math.max(0, currentDays);
        int months = Math.max(0, currentMonths);

        int order = Math.max(days, months * 30);

        float worldRadius = 50f
                + 28f * (float) Math.sqrt(order);


// 실제 별자리 위치를 기준으로 이동 범위 확장
        for (Constellation constellation : constellations) {

            Bitmap bitmap =
                    constellationBitmaps[constellation.month - 1];

            if (bitmap == null) {
                continue;
            }

            float height = constellation.width
                    * bitmap.getHeight()
                    / (float) bitmap.getWidth();

            float extentX = Math.abs(constellation.x)
                    + constellation.width / 2f;

            float extentY = Math.abs(constellation.y)
                    + height / 2f;

            worldRadius = Math.max(
                    worldRadius,
                    Math.max(extentX, extentY) + 50f
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

    @Override
    protected void onSizeChanged(
            int w, int h, int oldw, int oldh) {

        super.onSizeChanged(w, h, oldw, oldh);
        limitCamera();
    }

    // 드래그 및 핀치 줌
    @Override
    public boolean onTouchEvent(MotionEvent event) {

        scaleDetector.onTouchEvent(event);

        switch (event.getActionMasked()) {

            case MotionEvent.ACTION_DOWN:
                lastX = event.getX();
                lastY = event.getY();
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

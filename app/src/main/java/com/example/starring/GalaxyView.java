
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

    // 별 데이터
    private final ArrayList<Star> stars = new ArrayList<>();

    // 이미지 그리기
    private final Paint imagePaint = new Paint(
            Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG
    );

    private final RectF drawRect = new RectF();

    // 별 PNG 이미지
    private final Bitmap babyBitmap;
    private final Bitmap bigBitmap;

    // 화면 밀도
    private final float density;

    // 카메라 설정
    private float zoom = 1.0f;
    private float offsetX = 0f;
    private float offsetY = 0f;

    // 터치 제어
    private final ScaleGestureDetector scaleDetector;
    private float lastX;
    private float lastY;
    private boolean hadMultiTouch = false;

    // 현재 생성된 별 개수
    private int currentDays = -1;
    private int currentMonths = -1;

    // 카메라 확대/축소 범위
    private static final float MIN_ZOOM = 0.3f;
    private static final float MAX_ZOOM = 4.0f;

    // 별 배치를 위한 황금각
    private static final double GOLDEN_ANGLE =
            Math.PI * (3.0 - Math.sqrt(5.0));

    // 은하계 공간의 기본 간격 (dp)
    private static final float STAR_SPACING = 28f;

    // 별 정보 클래스
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

    public GalaxyView(Context context) {
        super(context);

        density = getResources()
                .getDisplayMetrics().density;

        // PNG 이미지 불러오기
        babyBitmap = BitmapFactory.decodeResource(
                getResources(),
                R.drawable.star_baby
        );

        bigBitmap = BitmapFactory.decodeResource(
                getResources(),
                R.drawable.star_big
        );

        setClickable(true);

        // 핀치 줌 감지
        scaleDetector = new ScaleGestureDetector(
                context,
                new ScaleGestureDetector
                        .SimpleOnScaleGestureListener() {

                    @Override
                    public boolean onScale(
                            ScaleGestureDetector detector) {

                        float oldZoom = zoom;

                        zoom *= detector.getScaleFactor();

                        // 최소/최대 줌 제한
                        zoom = Math.max(
                                MIN_ZOOM,
                                Math.min(zoom, MAX_ZOOM)
                        );

                        float ratio = zoom / oldZoom;

                        // 손가락 중심 위치
                        float focusX = detector.getFocusX()
                                - getWidth() / 2f;

                        float focusY = detector.getFocusY()
                                - getHeight() / 2f;

                        // 손가락 중심을 기준으로 확대
                        offsetX = focusX
                                - (focusX - offsetX) * ratio;

                        offsetY = focusY
                                - (focusY - offsetY) * ratio;

                        limitCamera();

                        invalidate();
                        return true;
                    }
                }
        );
    }

    // 날짜에 따라 별 생성
    public void setStarCounts(int days, int months) {

        days = Math.max(0, days);
        months = Math.max(0, months);

        // 기존 개수와 같으면 재생성하지 않음
        if (currentDays == days
                && currentMonths == months) {
            return;
        }

        currentDays = days;
        currentMonths = months;

        stars.clear();

        // 애기별: 하루에 하나
        for (int i = 1; i <= days; i++) {
            stars.add(createStar(i, false));
        }

        // 큰 별: 한 달에 하나
        for (int i = 1; i <= months; i++) {
            stars.add(createStar(i, true));
        }

        limitCamera();
        invalidate();
    }

    // 별의 위치와 크기 생성

    private Star createStar(int index, boolean big) {

        // 별마다 고정된 랜덤 시드 사용
        long seed = 913701L
                + index * 104729L
                + (big ? 500000L : 0L);

        Random random = new Random(seed);

        // 큰 별은 경과 개월을 날짜 규모로 환산
        int order = big ? index * 30 : index;

        // 별이 늘어나면 은하계 공간도 확장
        float maxRadius = 50f
                + 28f * (float) Math.sqrt(order);

        float x;
        float y;

        // 38%는 별이 모여 있는 구역에 생성
        boolean clustered =
                order > 20 && random.nextFloat() < 0.38f;

        if (clustered) {

            // 별이 모여 있는 네 개의 구역
            float[][] centers = {
                    {-170f, -140f},
                    {190f, -110f},
                    {-130f, 210f},
                    {180f, 200f}
            };

            int cluster = random.nextInt(centers.length);

            // 중심 주변에 불규칙하게 흩어짐
            x = centers[cluster][0]
                    + (float) random.nextGaussian() * 90f;

            y = centers[cluster][1]
                    + (float) random.nextGaussian() * 110f;

        } else {

            // 나머지 별은 전체 공간에 무작위 생성
            double angle =
                    random.nextDouble() * Math.PI * 2;

            float distance = maxRadius
                    * (float) Math.sqrt(random.nextDouble());

            x = (float) (Math.cos(angle) * distance);
            y = (float) (Math.sin(angle) * distance);
        }

        // 별 크기도 랜덤
        float size;

        if (big) {
            size = 13f + random.nextFloat() * 9f;
        } else {
            size = 5f + random.nextFloat() * 3f;
        }

        return new Star(x, y, size, big);
    }


    // 별 이미지 출력
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;

        for (Star star : stars) {

            // 은하계 좌표 → 화면 좌표
            float x = centerX + offsetX
                    + star.x * density * zoom;

            float y = centerY + offsetY
                    + star.y * density * zoom;

            // 별 자체 크기에는 zoom을 적용하지 않음
            float size = star.size * density;
            float half = size / 2f;

            // 화면 밖에 있는 별은 그리지 않음
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

    // 카메라 이동 범위 제한
    private void limitCamera() {

        if (getWidth() == 0 || getHeight() == 0) {
            return;
        }

        // 별의 분포 범위 계산
        int babySlot = currentDays > 0
                ? currentDays + (currentDays - 1) / 30
                : 0;

        int bigSlot = Math.max(0, currentMonths) * 31;

        int lastSlot = Math.max(babySlot, bigSlot);

        float worldRadius = (
                STAR_SPACING * (float) Math.sqrt(lastSlot)
                        + 50f
        ) * density * zoom;

        // 별이 없는 영역으로 무한히 이동하지 않도록 제한
        float maxX = Math.max(
                100f * density,
                worldRadius + getWidth() * 0.25f
        );

        float maxY = Math.max(
                100f * density,
                worldRadius + getHeight() * 0.25f
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

    // 화면 크기 변경 시 카메라 범위 갱신
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

package com.example.starring;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.example.starring.court.StarConsumption;


import java.util.Calendar;
import java.util.TimeZone;
import com.example.starring.court.CourtMainActivity;
import com.example.starring.court.CourtWriteActivity;
import com.example.starring.court.CourtNotice;
import com.example.starring.court.CourtRepository;
import com.example.starring.court.CourtPost;
import com.example.starring.court.CourtVoteActivity;
import com.example.starring.auth.LoginActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.example.starring.common.Navigator;

import java.util.List;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.DocumentSnapshot;


import java.util.ArrayList;
import java.util.Collections;


public class GalaxyActivity extends Activity {

    // 기존 은하계
    private GalaxyView galaxyView;
    private ImageView galaxyBackground;
    private FrameLayout galaxyContainer;

    // 태양계 메뉴
    private View solarDimOverlay;
    private LinearLayout solarMenuPanel;
    private LinearLayout solarMenuGrid;
    private ImageView btnSun;

    private boolean menuOpen = false;

    // 재판 데이터가 변경되면 메인 은하계 갱신
    private final Runnable courtObserver = () -> {
        refreshCourtGalaxy();
    };


    // 날짜 자동 갱신
    private final Handler handler =
            new Handler(Looper.getMainLooper());

    // Firebase에서 전달받는 실제 연애 시작일
    private int startYear = 2026;
    private int startMonth = 11;
    private int startDay = 19;

    // 정식 실행 시 날짜를 임의로 추가하지 않음
    private static final int TEST_ADD_DAYS = 0;

    // 행성 메뉴 이름
    private static final String[] MENU_NAMES = {
            "수성", "금성", "지구", "화성",
            "목성", "토성", "천왕성", "해왕성"
    };

    // 행성 메뉴 PNG
    private static final int[] MENU_IMAGES = {
            R.drawable.solar_mercury,
            R.drawable.solar_venus,
            R.drawable.solar_earth,
            R.drawable.solar_mars,
            R.drawable.solar_jupiter,
            R.drawable.solar_saturn,
            R.drawable.solar_uranus,
            R.drawable.solar_neptune
    };
    private ListenerRegistration planetsListener;
    private int planetsListenerGeneration = 0;

    // 블랙홀 아래 타이머 (1초마다 갱신) - na
    private final Runnable blackholeLabelTicker = new Runnable() {
        @Override
        public void run() {
            updateBlackholeLabel();
            handler.postDelayed(this, 1000);
        }
    };


    // 현재 커플의 진행 중인 블랙홀 조회
    private CourtPost getCurrentBlackhole() {

        List<CourtPost> open =
                CourtRepository.getMyPosts(false);

        if (open.isEmpty()) {
            return null;
        }

        // 최신 재판을 우선 표시
        return open.get(0);
    }

    // Firestore에서 재판 정보가 변경되면 호출
    private void refreshCourtGalaxy() {

        if (galaxyView == null) {
            return;
        }

        CourtPost post = getCurrentBlackhole();

        // 진행 중인 재판이 있으면 블랙홀 표시
        galaxyView.setBlackholeVisible(post != null);

        // 블랙홀 하단 시간 표시
        updateBlackholeLabel();

        // 삼킨 별 개수도 다시 계산
        updateGalaxy();
    }


    private final Runnable dateChecker = new Runnable() {
        @Override
        public void run() {
            updateGalaxy();
            handler.postDelayed(this, 60000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_galaxy);

        // 은하계 로딩 화면에서 받은 만난 날짜
        String startDate = getIntent().getStringExtra(
                GalaxyLoadingActivity.EXTRA_START_DATE
        );

        if (startDate != null
                && startDate.matches("\\d{4}-\\d{2}-\\d{2}")) {

            String[] parts = startDate.split("-");

            startYear = Integer.parseInt(parts[0]);
            startMonth = Integer.parseInt(parts[1]);
            startDay = Integer.parseInt(parts[2]);
        }

        // 기존 은하계
        galaxyBackground =
                findViewById(R.id.galaxyBackground);

        galaxyContainer =
                findViewById(R.id.galaxyContainer);

        galaxyView = new GalaxyView(this);

        galaxyView.setOnPlanetClickListener(planetId -> {

            Intent intent = new Intent(
                    GalaxyActivity.this,
                    PlanetDetailActivity.class
            );

            intent.putExtra(
                    PlanetDetailActivity.EXTRA_PLANET_ID,
                    planetId
            );

            startActivity(intent);
        });

        // 은하계의 블랙홀을 누르면 내 블랙홀의 투표 현황 화면으로 이동 - na
        galaxyView.setOnBlackholeClickListener(() -> {
            List<CourtPost> open = CourtRepository.getMyPosts(false);
            if (open.isEmpty()) {
                return;
            }
            Intent intent = new Intent(GalaxyActivity.this, CourtVoteActivity.class);
            intent.putExtra(CourtVoteActivity.EXTRA_POST_ID, open.get(0).id);
            startActivity(intent);
        });

        galaxyContainer.addView(
                galaxyView,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                )
        );

        // 태양계 메뉴 초기화
        initSolarMenu();
    }

    // ------------------------------------
    // 1. 태양계 메뉴 초기화
    // ------------------------------------

    private void initSolarMenu() {

        solarDimOverlay =
                findViewById(R.id.solarDimOverlay);

        solarMenuPanel =
                findViewById(R.id.solarMenuPanel);

        solarMenuGrid =
                findViewById(R.id.solarMenuGrid);

        btnSun =
                findViewById(R.id.btnSun);

        // 태양 클릭
        btnSun.setOnClickListener(v -> {
            setMenuOpen(!menuOpen);
        });

        // 메뉴 바깥 클릭
        solarDimOverlay.setOnClickListener(v -> {
            setMenuOpen(false);
        });

        // 행성 메뉴 이미지 생성
        createPlanetMenu();
    }

    // ------------------------------------
    // 2. 메뉴 열기 / 닫기
    // ------------------------------------

    private void setMenuOpen(boolean open) {

        menuOpen = open;

        solarDimOverlay.setVisibility(
                open ? View.VISIBLE : View.GONE
        );

        solarMenuPanel.setVisibility(
                open ? View.VISIBLE : View.GONE
        );

        btnSun.setContentDescription(
                open
                        ? "태양계 메뉴 닫기"
                        : "태양계 메뉴 열기"
        );

        // Android 12 이상 블러 적용
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {

            if (open) {

                RenderEffect blur =
                        RenderEffect.createBlurEffect(
                                dp(12),
                                dp(12),
                                Shader.TileMode.CLAMP
                        );

                galaxyBackground.setRenderEffect(blur);
                galaxyContainer.setRenderEffect(blur);

            } else {

                galaxyBackground.setRenderEffect(null);
                galaxyContainer.setRenderEffect(null);
            }
        }
    }

    // ------------------------------------
    // 3. 8개 행성 PNG 메뉴 생성
    // ------------------------------------

    private void createPlanetMenu() {

        solarMenuGrid.removeAllViews();

        // 4개씩 2줄 배치
        for (int rowIndex = 0; rowIndex < 2; rowIndex++) {

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER);

            for (int col = 0; col < 4; col++) {

                final int index = rowIndex * 4 + col;

                // 행성 메뉴 하나
                LinearLayout item = new LinearLayout(this);

                item.setOrientation(LinearLayout.VERTICAL);
                item.setGravity(Gravity.CENTER);

                item.setClickable(true);
                item.setFocusable(true);

                item.setContentDescription(
                        MENU_NAMES[index] + " 메뉴"
                );

                // 행성 PNG
                ImageView planetImage = new ImageView(this);

                planetImage.setImageResource(
                        MENU_IMAGES[index]
                );

                planetImage.setScaleType(
                        ImageView.ScaleType.FIT_CENTER
                );

                planetImage.setContentDescription(null);

                // PNG 표시 크기
                LinearLayout.LayoutParams imageParams =
                        new LinearLayout.LayoutParams(
                                dp(60),
                                dp(60)
                        );

                item.addView(
                        planetImage,
                        imageParams
                );

                // 행성 이름
                TextView planetName = new TextView(this);

                planetName.setText(MENU_NAMES[index]);
                planetName.setTextSize(13f);
                planetName.setTextColor(
                        Color.parseColor("#EEE5F9")
                );

                planetName.setGravity(Gravity.CENTER);

                LinearLayout.LayoutParams textParams =
                        new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                dp(25)
                        );

                textParams.topMargin = dp(4);

                item.addView(
                        planetName,
                        textParams
                );

                // 행성 클릭 이벤트
                item.setOnClickListener(v -> {
                    onPlanetSelected(index);
                });

                // 각 메뉴가 동일한 가로 폭 사용
                LinearLayout.LayoutParams itemParams =
                        new LinearLayout.LayoutParams(
                                0,
                                dp(100),
                                1f
                        );

                row.addView(item, itemParams);
            }

            solarMenuGrid.addView(
                    row,
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                    )
            );
        }
    }

    // ------------------------------------
    // 4. 행성별 화면 이동
    // ------------------------------------

    private void onPlanetSelected(int index) {

        // 먼저 메뉴 닫기
        setMenuOpen(false);

        switch (index) {

            case 0:
                // 수성 - 행성 생성 화면
                startActivity(
                        new Intent(
                                this,
                                PlanetCreateActivity.class
                        )
                );
                break;

            case 1:
                // 금성 - 블랙홀 생성 화면
                startActivity(
                        new Intent(
                                this,
                                CourtWriteActivity.class
                        )
                );
                break;

            case 2:
                // 지구 - 우주 재판장 메인
                startActivity(
                        new Intent(
                                this,
                                CourtMainActivity.class
                        )
                );
                break;

            case 3:
                // 화성 - 행성 생성 화면
                startActivity(
                        new Intent(
                                this,
                                PlanetCreateActivity.class
                        )
                );
                break;

            case 4:
                // 목성 - 메인 은하계
                // 이미 메인 화면이므로 이동하지 않음
                break;

            case 5:
                // 토성 - 기능 없음
                showComingSoon("토성");
                break;

            case 6:
                // 천왕성 - 로그아웃
                confirmLogout();
                break;

            case 7:
                // 해왕성 - 은하계 소멸
                startActivity(
                        new Intent(
                                this,
                                GalaxyDestroyActivity.class
                        )
                );
                break;
        }
    }

    private void showComingSoon(String name) {

        Toast.makeText(
                this,
                name + " 기능은 준비 중입니다.",
                Toast.LENGTH_SHORT
        ).show();
    }

    // ------------------------------------
    // 5. dp 변환
    // ------------------------------------

    private int dp(float value) {

        return Math.round(
                value * getResources()
                        .getDisplayMetrics().density
        );
    }

    // ------------------------------------
    // 6. 날짜 기반 천체 생성
    // ------------------------------------

    private Calendar makeDate(
            int year, int month, int day) {

        Calendar date = Calendar.getInstance(
                TimeZone.getTimeZone("UTC")
        );

        date.clear();
        date.set(year, month - 1, day);

        return date;
    }

    // 매월 1일 별자리 생성
    private int calculateConstellationMask(
            Calendar start, Calendar today) {

        if (today.before(start)) {
            return 0;
        }

        Calendar first = (Calendar) start.clone();
        first.set(Calendar.DAY_OF_MONTH, 1);

        if (first.before(start)) {
            first.add(Calendar.MONTH, 1);
        }

        int mask = 0;

        while (!first.after(today)) {

            int monthIndex =
                    first.get(Calendar.MONTH);

            // 이미 있는 별자리는 추가되지 않음
            mask |= (1 << monthIndex);

            // 12종 모두 생성되면 종료
            if (mask == 0xFFF) {
                break;
            }

            first.add(Calendar.MONTH, 1);
        }

        return mask;
    }

    private void updateGalaxy() {

        Calendar localNow = Calendar.getInstance();

        Calendar today = makeDate(
                localNow.get(Calendar.YEAR),
                localNow.get(Calendar.MONTH) + 1,
                localNow.get(Calendar.DAY_OF_MONTH)
        );

        // 날짜 시뮬레이션
        today.add(
                Calendar.DAY_OF_MONTH,
                TEST_ADD_DAYS
        );

        Calendar start = makeDate(
                startYear,
                startMonth,
                startDay
        );

        // 경과 일수
        long diff =
                today.getTimeInMillis()
                        - start.getTimeInMillis();

        int days = (int) Math.max(
                0,
                diff / 86400000L
        );

        // 경과 개월 수
        int months = 0;

        if (!today.before(start)) {

            while (true) {

                Calendar next = (Calendar) start.clone();

                next.add(
                        Calendar.MONTH,
                        months + 1
                );

                if (next.after(today)) {
                    break;
                }

                months++;
            }
        }

        int constellationMask =
                calculateConstellationMask(start, today);


        // 원래 가지고 있는 애기별 개수
        int originalBabyStars = days;

        // 블랙홀이 삼킨 애기별 개수
        int swallowedStars = 0;

        CourtPost blackhole = getCurrentBlackhole();

        if (blackhole != null
                && blackhole.createdAt > 0L) {

            swallowedStars = StarConsumption.swallowed(
                    blackhole.createdAt,
                    blackhole.resolvedAt,
                    System.currentTimeMillis(),
                    originalBabyStars
            );
        }

        // 별 개수는 0보다 작아지지 않도록 제한
        int remainingBabyStars = Math.max(
                0,
                originalBabyStars - swallowedStars
        );

        // 애기별은 삼킨 만큼 감소
        // 큰 별은 기존 월별 생성 개수 유지
        galaxyView.setStarCounts(
                remainingBabyStars,
                months
        );


        // 기존 별자리 생성
        galaxyView.setConstellationMask(
                constellationMask
        );

        // 기존 초신성 / 백색왜성 테스트
        galaxyView.setSpecialBodyCounts(1, 1);
    }

    // ------------------------------------
    // 7. 생명주기
    // ------------------------------------


    private void updateBlackholeLabel() {

        CourtPost post = getCurrentBlackhole();

        if (post == null) {

            galaxyView.setBlackholeLabel(
                    null,
                    false
            );

            return;
        }

        if (post.isExpired()) {

            galaxyView.setBlackholeLabel(
                    "마감됨",
                    false
            );

        } else {

            galaxyView.setBlackholeLabel(
                    post.remainingClock(),
                    post.isUrgent()
            );
        }
    }



    @Override
    protected void onResume() {
        super.onResume();

        CourtNotice.showPendingIfAny(this);

        // Firebase 재판 데이터 변경 감지 등록
        CourtRepository.addObserver(courtObserver);

        // 현재 데이터 기준으로 바로 갱신
        refreshCourtGalaxy();

        // 블랙홀 남은 시간 1초마다 갱신
        handler.removeCallbacks(blackholeLabelTicker);
        handler.post(blackholeLabelTicker);

        // 별 개수 1분마다 재계산
        handler.removeCallbacks(dateChecker);
        handler.post(dateChecker);

        startPlanetsListener();
    }

    @Override
    protected void onPause() {

        // 화면이 보이지 않을 때 콜백 해제
        CourtRepository.removeObserver(courtObserver);

        handler.removeCallbacks(dateChecker);
        handler.removeCallbacks(blackholeLabelTicker);

        super.onPause();

        stopPlanetsListener();
    }


    // 뒤로가기 시 메뉴가 열려 있다면 닫기
    @Override
    public void onBackPressed() {

        if (menuOpen) {
            setMenuOpen(false);
        } else {
            super.onBackPressed();
        }
    }


    private void startPlanetsListener() {

        final int generation = ++planetsListenerGeneration;

        FirebaseUser user =
                FirebaseAuth.getInstance().getCurrentUser();

        if (user == null) {
            return;
        }

        FirebaseFirestore db =
                FirebaseFirestore.getInstance();

        // 로그인한 계정의 은하계 ID 확인
        db.collection("users")
                .document(user.getUid())
                .get()
                .addOnSuccessListener(this, userDoc -> {

                    if (generation != planetsListenerGeneration) {
                        return;
                    }

                    String galaxyId =
                            userDoc.getString("galaxyId");

                    if (galaxyId == null) {

                        galaxyView.setPlanetDocuments(
                                Collections.emptyList(),
                                Collections.emptyList()
                        );

                        return;
                    }

                    // 실제 생성된 행성 실시간 조회
                    planetsListener = db.collection("galaxies")
                            .document(galaxyId)
                            .collection("planets")
                            .orderBy(
                                    "createdAt",
                                    Query.Direction.ASCENDING
                            )
                            .limit(24)
                            .addSnapshotListener((snapshot, error) -> {

                                if (generation != planetsListenerGeneration) {
                                    return;
                                }

                                if (error != null) {

                                    android.util.Log.e(
                                            "StarRingPlanets",
                                            "행성 목록 조회 실패",
                                            error
                                    );

                                    return;
                                }

                                if (snapshot == null) {
                                    return;
                                }

                                ArrayList<String> ids =
                                        new ArrayList<>();

                                ArrayList<String> assets =
                                        new ArrayList<>();

                                for (DocumentSnapshot doc :
                                        snapshot.getDocuments()) {

                                    ids.add(doc.getId());

                                    String asset =
                                            doc.getString("planetAsset");

                                    assets.add(
                                            asset == null
                                                    ? "planet_01"
                                                    : asset
                                    );
                                }

                                // GalaxyView의 실제 행성 목록 반영
                                galaxyView.setPlanetDocuments(
                                        ids,
                                        assets
                                );
                            });
                })
                .addOnFailureListener(this, error -> {

                    android.util.Log.e(
                            "StarRingPlanets",
                            "은하계 정보 조회 실패",
                            error
                    );
                });
    }

    private void stopPlanetsListener() {

        planetsListenerGeneration++;

        if (planetsListener != null) {
            planetsListener.remove();
            planetsListener = null;
        }
    }



    private void confirmLogout() {

        new android.app.AlertDialog.Builder(this)
                .setTitle("로그아웃")
                .setMessage("정말 로그아웃하시겠습니까?")
                .setNegativeButton("취소", null)
                .setPositiveButton("로그아웃", (dialog, which) -> {

                    // Firebase 로그인 세션 종료
                    FirebaseAuth.getInstance().signOut();

                    // 로그인 화면으로 이동
                    // 기존 화면 스택도 모두 제거
                    Navigator.toLogin(this);
                })
                .show();
    }



}

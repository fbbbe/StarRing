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


import com.example.starring.common.GalaxyDates;

import java.time.LocalDate;
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
import com.example.starring.common.Navigator;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;


import java.util.ArrayList;
import java.util.Collections;


public class GalaxyActivity extends Activity {

    // 기존 은하계
    private GalaxyView galaxyView;
    private ImageView galaxyBackground;
    private FrameLayout galaxyContainer;

    // 태양계 메뉴
    private View solarDimOverlay;
    private SolarOrbitMenuView solarMenu;
    private ImageView btnSun;

    private boolean menuOpen = false;

    // 재판 데이터가 변경되면 메인 은하계 갱신
    private final Runnable courtObserver = () -> {
        refreshCourtGalaxy();
    };


    // 날짜 자동 갱신
    private final Handler handler =
            new Handler(Looper.getMainLooper());

    // Firebase에서 전달받는 실제 연애 시작일 (전달값이 없거나 잘못되면 기본값 유지)
    private LocalDate startDate = LocalDate.of(2026, 11, 19);

    // 정식 실행 시 날짜를 임의로 추가하지 않음
    private static final int TEST_ADD_DAYS = 0;

    // 행성 메뉴 이름
    private static final String[] MENU_NAMES = {
            "수성", "금성", "지구", "화성",
            "목성", "토성", "천왕성", "해왕성"
    };

    private ListenerRegistration connectionListener;

    // 메뉴 역할 (행성 이름 아래 알약, 앞에 → 는 자동)
    private static final String[] MENU_ACTIONS = {
            "행성 생성",     // 수성
            "블랙홀 생성",   // 금성
            "우주 재판장",   // 지구
            "이름 변경",     // 화성 (준비 중)
            "메인",          // 목성
            "행성 둘러보기", // 토성
            "로그아웃",      // 천왕성
            "연결 끊기"      // 해왕성
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

        // 우측 상단 은하계 이름 (로딩 화면에서 받은 이름)
        TextView textGalaxyName = findViewById(R.id.textGalaxyName);

        String galaxyName = getIntent().getStringExtra(
                GalaxyLoadingActivity.EXTRA_GALAXY_NAME
        );

        if (galaxyName != null && !galaxyName.trim().isEmpty()) {
            textGalaxyName.setText(galaxyName.trim() + " 은하계");
            textGalaxyName.setVisibility(View.VISIBLE);
        }

        // 은하계 로딩 화면에서 받은 만난 날짜
        // "yyyy-MM-dd" → 날짜. 형식이 틀리거나 없는 날짜(2/31 등)면 null → 기본값 유지
        LocalDate parsedStart = GalaxyDates.parseDate(
                getIntent().getStringExtra(
                        GalaxyLoadingActivity.EXTRA_START_DATE
                )
        );

        if (parsedStart != null) {
            startDate = parsedStart;
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

        solarMenu =
                findViewById(R.id.solarMenu);

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

        // 궤도 메뉴: 태양 중심 위치 알려주기 (btnSun 의 하단 여백 + 높이/2)
        FrameLayout.LayoutParams sunLp =
                (FrameLayout.LayoutParams) btnSun.getLayoutParams();
        solarMenu.setSunCenterFromBottom(
                sunLp.bottomMargin + sunLp.height / 2
        );

        // 8개 항목 (실제 태양계 순서: 안쪽 궤도 = 수성)
        List<SolarOrbitMenuView.Item> items = new ArrayList<>();
        for (int i = 0; i < MENU_NAMES.length; i++) {
            items.add(new SolarOrbitMenuView.Item(
                    MENU_IMAGES[i],
                    MENU_NAMES[i],
                    MENU_ACTIONS[i]
            ));
        }
        solarMenu.setItems(items);
        solarMenu.setOnItemSelectedListener(this::onPlanetSelected);
    }

    // ------------------------------------
    // 2. 메뉴 열기 / 닫기
    // ------------------------------------

    private void setMenuOpen(boolean open) {

        menuOpen = open;

        solarDimOverlay.setVisibility(
                open ? View.VISIBLE : View.GONE
        );

        if (open) {
            solarMenu.open();
        } else {
            solarMenu.close();
        }

        // 열리면 태양이 처음보다 확실히 커지고, 닫으면 원래 크기로
        btnSun.animate()
                .scaleX(open ? 1.25f : 1f)
                .scaleY(open ? 1.25f : 1f)
                .setDuration(250)
                .start();

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
    // 4. 행성별 화면 이동
    // ------------------------------------

    private void onPlanetSelected(int index) {

        // 먼저 메뉴 닫기
        setMenuOpen(false);

        switch (index) {

            case 0:
                // 수성 - 행성 생성
                startActivity(new Intent(this, PlanetCreateActivity.class));
                break;

            case 1:
                // 금성 - 블랙홀 생성 (재판장 작성)
                startActivity(new Intent(this, CourtWriteActivity.class));
                break;

            case 2:
                // 지구 - 우주 재판장
                startActivity(new Intent(this, CourtMainActivity.class));
                break;

            case 3:
                // 화성 - 은하계 이름 변경 (아직 미구현: 규칙 변경이 필요해 보류)
                showComingSoon("은하계 이름 변경");
                break;

            case 4:
                // 목성 - 메인 페이지 (이미 메인이므로 이동 없음)
                break;

            case 5:
                // 토성 - 행성 둘러보기
                showPlanetBrowser();
                break;

            case 6:
                // 천왕성 - 로그아웃
                confirmLogout();
                break;

            case 7:
                // 해왕성 - 탈퇴 / 연결 끊기 (은하계 소멸)
                startActivity(new Intent(this, GalaxyDestroyActivity.class));
                break;
        }
    }

    // ------------------------------------
    // 토성: 행성 둘러보기 (달별 행성 목록 → 상세)
    // ------------------------------------

    private void showPlanetBrowser() {

        loadGalaxyId(galaxyId ->

                FirebaseFirestore.getInstance()
                        .collection("galaxies")
                        .document(galaxyId)
                        .collection("planets")
                        .orderBy("createdAt", Query.Direction.ASCENDING)
                        .limit(24)
                        .get()
                        .addOnSuccessListener(this, snapshot -> {

                            List<DocumentSnapshot> docs = snapshot.getDocuments();

                            if (docs.isEmpty()) {
                                Toast.makeText(this,
                                        "아직 행성이 없어요. 수성에서 만들어 보세요!",
                                        Toast.LENGTH_SHORT).show();
                                return;
                            }

                            // 최신 달이 위로 오게
                            final String[] ids = new String[docs.size()];
                            final String[] labels = new String[docs.size()];

                            for (int i = 0; i < docs.size(); i++) {

                                DocumentSnapshot doc = docs.get(docs.size() - 1 - i);

                                String key = PlanetMonth.of(doc);
                                String month = key != null
                                        ? PlanetMonth.label(key)
                                        : "";

                                Long count = doc.getLong("photoCount");

                                ids[i] = doc.getId();
                                labels[i] = (month.isEmpty() ? "행성" : month + " 행성")
                                        + "  ·  사진 "
                                        + (count == null ? 0 : count) + "장";
                            }

                            new android.app.AlertDialog.Builder(
                                    this, R.style.StarRing_LightDialog)
                                    .setTitle("행성 둘러보기")
                                    .setItems(labels, (dialog, which) -> {
                                        Intent intent = new Intent(
                                                GalaxyActivity.this,
                                                PlanetDetailActivity.class
                                        );
                                        intent.putExtra(
                                                PlanetDetailActivity.EXTRA_PLANET_ID,
                                                ids[which]
                                        );
                                        startActivity(intent);
                                    })
                                    .setNegativeButton("닫기", null)
                                    .show();
                        })
                        .addOnFailureListener(this, e -> {
                            android.util.Log.e("StarRingMenu", "행성 목록 조회 실패", e);
                            Toast.makeText(this, "행성 목록을 불러오지 못했어요.",
                                    Toast.LENGTH_LONG).show();
                        })
        );
    }

    // 로그인한 계정의 은하계 ID 조회 (행성 둘러보기용)
    private void loadGalaxyId(java.util.function.Consumer<String> callback) {

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

        if (user == null) {
            return;
        }

        FirebaseFirestore.getInstance()
                .collection("users")
                .document(user.getUid())
                .get()
                .addOnSuccessListener(this, userDoc -> {

                    String galaxyId = userDoc.getString("galaxyId");

                    if (galaxyId == null) {
                        Toast.makeText(this, "은하계 정보를 찾을 수 없어요.",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    callback.accept(galaxyId);
                })
                .addOnFailureListener(this, e ->
                        Toast.makeText(this, "정보를 불러오지 못했어요.",
                                Toast.LENGTH_SHORT).show());
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

    // 날짜 계산은 common/GalaxyDates (CourtReconcileActivity와 같은 계산)
    private void updateGalaxy() {

        // 오늘 (기기 시간대 날짜) + 날짜 시뮬레이션
        LocalDate today = GalaxyDates.today()
                .plusDays(TEST_ADD_DAYS);

        // 경과 일수
        int days = GalaxyDates.elapsedDays(startDate, today);

        // 경과 개월 수
        int months = GalaxyDates.elapsedMonths(startDate, today);

        // 매월 1일 별자리
        int constellationMask =
                GalaxyDates.constellationMask(startDate, today);


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

        // 특수천체: 1주년마다 초신성(크게 그려짐 = 많이 화려),
        // 100일마다 백색왜성(작게 그려짐 = 덜 화려). 각각 GalaxyView에서 최대 20개로 제한
        galaxyView.setSpecialBodyCounts(
                GalaxyDates.yearCount(startDate, today),
                GalaxyDates.hundredDayCount(startDate, today)
        );
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

        // 마감된 블랙홀도 시간 대신 글자를 바꾸지 않고 00:00:00 으로 표시하고,
// 마감됐거나 1시간 미만이면 빨간색으로 깜빡임
        galaxyView.setBlackholeLabel(
                post.remainingClock(),
                post.isExpired() || post.isUrgent()
        );
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


    private void startConnectionWatcher() {

        if (connectionListener != null) return;

        FirebaseUser user =
                FirebaseAuth.getInstance().getCurrentUser();

        if (user == null) {
            Navigator.toLogin(this);
            return;
        }

        connectionListener = FirebaseFirestore
                .getInstance()
                .collection("users")
                .document(user.getUid())
                .addSnapshotListener((snapshot, error) -> {

                    if (error != null
                            || snapshot == null
                            || !snapshot.exists()) {
                        return;
                    }

                    // 아직 서버에 확정되지 않은 변경은 무시
                    if (snapshot.getMetadata().hasPendingWrites()) {
                        return;
                    }

                    String galaxyId =
                            snapshot.getString("galaxyId");

                    if (galaxyId == null) {

                        stopConnectionWatcher();

                        FirebaseAuth.getInstance().signOut();

                        // 상대방도 로그인 화면으로 이동
                        Navigator.toLogin(this);
                    }
                });
    }

    private void stopConnectionWatcher() {

        if (connectionListener != null) {
            connectionListener.remove();
            connectionListener = null;
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        startConnectionWatcher();
    }

    @Override
    protected void onStop() {
        stopConnectionWatcher();
        super.onStop();
    }




}

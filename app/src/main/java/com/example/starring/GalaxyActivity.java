
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

import com.example.starring.common.GalaxyDates;
import com.example.starring.common.Navigator;
import com.example.starring.court.StarConsumption;
import com.example.starring.court.CourtMainActivity;
import com.example.starring.court.CourtWriteActivity;
import com.example.starring.court.CourtNotice;
import com.example.starring.court.CourtRepository;
import com.example.starring.court.CourtPost;
import com.example.starring.court.CourtVoteActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.DocumentSnapshot;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GalaxyActivity extends Activity {

    // ====================================
    // 은하계 화면
    // ====================================

    private GalaxyView galaxyView;
    private ImageView galaxyBackground;
    private FrameLayout galaxyContainer;

    // ====================================
    // 태양계 메뉴
    // ====================================

    private View solarDimOverlay;
    private SolarOrbitMenuView solarMenu;
    private ImageView btnSun;

    private boolean menuOpen = false;

    private static final String[] MENU_NAMES = {
            "수성", "금성", "지구", "화성",
            "목성", "토성", "천왕성", "해왕성"
    };

    // 메뉴 역할 (화면에는 안 보이고 접근성 설명에만 사용)
    private static final String[] MENU_ACTIONS = {
            "행성 생성",         // 수성
            "블랙홀 생성",       // 금성
            "우주 재판장",       // 지구
            "은하계 이름 변경",  // 화성
            "은하계",            // 목성
            "행성 둘러보기",     // 토성
            "로그아웃",          // 천왕성
            "탈퇴/연결 끊기"     // 해왕성
    };

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

    // ====================================
    // 은하계 이름 + D-day
    // ====================================

    private TextView textGalaxyName;

    private String currentGalaxyName;

    private LocalDate startDate =
            LocalDate.of(2026, 11, 19);

    private boolean hasStartDate = false;

    private ListenerRegistration galaxyInfoListener;
    private String watchedGalaxyId;

    // 정식 실행 시 임의로 날짜를 추가하지 않음
    private static final int TEST_ADD_DAYS = 0;

    // ====================================
    // Firebase 리스너
    // ====================================

    private ListenerRegistration connectionListener;

    private ListenerRegistration planetsListener;
    private int planetsListenerGeneration = 0;

    // ====================================
    // 날짜 자동 갱신
    // ====================================

    private final Handler handler =
            new Handler(Looper.getMainLooper());

    private final Runnable dateChecker =
            new Runnable() {
                @Override
                public void run() {
                    updateGalaxy();
                    handler.postDelayed(this, 60000);
                }
            };

    // ====================================
    // 재판 관련
    // ====================================

    private final Runnable courtObserver = () -> {
        refreshCourtGalaxy();
    };

    private final Runnable blackholeLabelTicker =
            new Runnable() {
                @Override
                public void run() {
                    updateBlackholeLabel();
                    handler.postDelayed(this, 1000);
                }
            };

    private CourtPost getCurrentBlackhole() {

        List<CourtPost> open =
                CourtRepository.getMyPosts(false);

        if (open.isEmpty()) {
            return null;
        }

        return open.get(0);
    }

    private void refreshCourtGalaxy() {

        if (galaxyView == null) {
            return;
        }

        CourtPost post = getCurrentBlackhole();

        galaxyView.setBlackholeVisible(post != null);

        updateBlackholeLabel();
        updateGalaxy();
    }

    // ====================================
    // onCreate
    // ====================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_galaxy);

        // 상단 은하계 이름 TextView
        textGalaxyName =
                findViewById(R.id.textGalaxyName);

        // 로딩 화면에서 전달받은 은하계 이름
        // Firestore 데이터가 도착하면 최신 이름으로 교체됨
        currentGalaxyName =
                getIntent().getStringExtra(
                        GalaxyLoadingActivity.EXTRA_GALAXY_NAME
                );

        // 로딩 화면에서 전달받은 만난 날짜
        LocalDate parsedStart =
                GalaxyDates.parseDate(
                        getIntent().getStringExtra(
                                GalaxyLoadingActivity.EXTRA_START_DATE
                        )
                );

        if (parsedStart != null) {
            startDate = parsedStart;
            hasStartDate = true;
        }

        // 최초 이름 + 경과 일수 표시
        updateGalaxyNameLabel();

        // 은하계 화면
        galaxyBackground =
                findViewById(R.id.galaxyBackground);

        galaxyContainer =
                findViewById(R.id.galaxyContainer);

        galaxyView = new GalaxyView(this);

        // 행성 클릭 시 상세 화면
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

        // 블랙홀 클릭 시 재판 투표 화면
        galaxyView.setOnBlackholeClickListener(() -> {

            List<CourtPost> open =
                    CourtRepository.getMyPosts(false);

            if (open.isEmpty()) {
                return;
            }

            Intent intent = new Intent(
                    GalaxyActivity.this,
                    CourtVoteActivity.class
            );

            intent.putExtra(
                    CourtVoteActivity.EXTRA_POST_ID,
                    open.get(0).id
            );

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

    // ====================================
    // 은하계 이름 + 경과 일수 표시
    // ====================================

    private void updateGalaxyNameLabel() {

        if (textGalaxyName == null) {
            return;
        }

        if (currentGalaxyName == null
                || currentGalaxyName.trim().isEmpty()) {

            textGalaxyName.setVisibility(View.GONE);
            return;
        }

        String label =
                currentGalaxyName.trim() + " 은하계";

        // 실제 만난 날짜가 있는 경우에만 D-day 표시
        if (hasStartDate) {

            LocalDate today =
                    GalaxyDates.today()
                            .plusDays(TEST_ADD_DAYS);

            int days = Math.max(
                    0,
                    GalaxyDates.elapsedDays(
                            startDate,
                            today
                    )
            );

            // 예: 우리의 은하계 +34
            label += "  +" + days;
        }

        textGalaxyName.setText(label);
        textGalaxyName.setVisibility(View.VISIBLE);
    }

    // ====================================
    // 은하계 이름 실시간 감시
    // ====================================

    private void startGalaxyInfoWatcher(
            String galaxyId) {

        if (galaxyId == null || galaxyId.isEmpty()) {
            return;
        }

        // 같은 은하계를 이미 감시하고 있다면
        // 리스너를 중복 생성하지 않음
        if (galaxyInfoListener != null
                && galaxyId.equals(watchedGalaxyId)) {
            return;
        }

        stopGalaxyInfoWatcher();

        watchedGalaxyId = galaxyId;

        galaxyInfoListener =
                FirebaseFirestore.getInstance()
                        .collection("galaxies")
                        .document(galaxyId)
                        .addSnapshotListener(
                                (snapshot, error) -> {

                                    if (!galaxyId.equals(
                                            watchedGalaxyId)) {
                                        return;
                                    }

                                    if (error != null) {

                                        android.util.Log.e(
                                                "StarRingGalaxy",
                                                "은하계 이름 조회 실패",
                                                error
                                        );

                                        return;
                                    }

                                    if (snapshot == null
                                            || !snapshot.exists()) {
                                        return;
                                    }

                                    // Firestore의 최신 은하계 이름
                                    String updatedName =
                                            snapshot.getString("name");

                                    if (updatedName != null
                                            && !updatedName.trim().isEmpty()) {

                                        currentGalaxyName =
                                                updatedName;
                                    }

                                    // Firestore의 실제 만난 날짜
                                    LocalDate updatedStart =
                                            GalaxyDates.parseDate(
                                                    snapshot.getString(
                                                            "datingStartDate"
                                                    )
                                            );

                                    if (updatedStart != null) {

                                        startDate = updatedStart;
                                        hasStartDate = true;
                                    }

                                    // 화면의 이름과 D-day 갱신
                                    updateGalaxyNameLabel();

                                    // 날짜에 따른 천체도 갱신
                                    updateGalaxy();
                                }
                        );
    }

    private void stopGalaxyInfoWatcher() {

        watchedGalaxyId = null;

        if (galaxyInfoListener != null) {

            galaxyInfoListener.remove();
            galaxyInfoListener = null;
        }
    }

    // ====================================
    // 태양계 메뉴 초기화
    // ====================================

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

        // 메뉴 바깥 영역 클릭
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

    // ====================================
    // 태양계 메뉴 열기 / 닫기
    // ====================================

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

        // Android 12 이상 블러
        if (Build.VERSION.SDK_INT
                >= Build.VERSION_CODES.S) {

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

    // ====================================
    // 태양계 메뉴 클릭
    // ====================================

    private void onPlanetSelected(int index) {

        setMenuOpen(false);

        switch (index) {

            case 0:
                // 수성 - 행성 생성
                startActivity(
                        new Intent(
                                this,
                                PlanetCreateActivity.class
                        )
                );
                break;

            case 1:
                // 금성 - 블랙홀 생성
                startActivity(
                        new Intent(
                                this,
                                CourtWriteActivity.class
                        )
                );
                break;

            case 2:
                // 지구 - 우주 재판장
                startActivity(
                        new Intent(
                                this,
                                CourtMainActivity.class
                        )
                );
                break;

            case 3:
                // 화성 - 은하계 이름 변경
                startActivity(
                        new Intent(
                                this,
                                GalaxyRenameActivity.class
                        )
                );
                break;

            case 4:
                // 목성 - 은하계 (이미 메인 은하계 화면이므로 이동 없음)
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
                // 해왕성 - 탈퇴/연결 끊기 (은하계 소멸)
                startActivity(
                        new Intent(
                                this,
                                GalaxyDestroyActivity.class
                        )
                );
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

    // ====================================
    // dp 변환
    // ====================================

    private int dp(float value) {

        return Math.round(
                value * getResources()
                        .getDisplayMetrics().density
        );
    }

    // ====================================
    // 날짜 기반 천체 생성
    // ====================================

    private void updateGalaxy() {

        // 날짜 변경에 맞춰 D-day 갱신
        updateGalaxyNameLabel();

        LocalDate today =
                GalaxyDates.today()
                        .plusDays(TEST_ADD_DAYS);

        // 경과 일수
        int days =
                GalaxyDates.elapsedDays(
                        startDate,
                        today
                );

        // 경과 개월
        int months =
                GalaxyDates.elapsedMonths(
                        startDate,
                        today
                );

        // 매월 1일 별자리
        int constellationMask =
                GalaxyDates.constellationMask(
                        startDate,
                        today
                );

        int originalBabyStars = days;

        // 블랙홀이 삼킨 별 개수
        int swallowedStars = 0;

        CourtPost blackhole = getCurrentBlackhole();

        if (blackhole != null
                && blackhole.createdAt > 0L) {

            swallowedStars =
                    StarConsumption.swallowed(
                            blackhole.createdAt,
                            blackhole.resolvedAt,
                            System.currentTimeMillis(),
                            originalBabyStars
                    );
        }

        int remainingBabyStars = Math.max(
                0,
                originalBabyStars - swallowedStars
        );

        // 애기별 / 큰 별
        galaxyView.setStarCounts(
                remainingBabyStars,
                months
        );

        // 별자리
        galaxyView.setConstellationMask(
                constellationMask
        );

        // 초신성 / 백색왜성
        galaxyView.setSpecialBodyCounts(
                GalaxyDates.yearCount(startDate, today),
                GalaxyDates.hundredDayCount(startDate, today)
        );
    }

    // ====================================
    // 블랙홀 타이머
    // ====================================

    private void updateBlackholeLabel() {

        CourtPost post = getCurrentBlackhole();

        if (post == null) {

            galaxyView.setBlackholeLabel(
                    null,
                    false
            );

            return;
        }

        galaxyView.setBlackholeLabel(
                post.remainingClock(),
                post.isExpired() || post.isUrgent()
        );
    }

    // ====================================
    // 생명주기
    // ====================================

    @Override
    protected void onResume() {
        super.onResume();

        CourtNotice.showPendingIfAny(this);

        CourtRepository.addObserver(courtObserver);

        refreshCourtGalaxy();

        handler.removeCallbacks(blackholeLabelTicker);
        handler.post(blackholeLabelTicker);

        handler.removeCallbacks(dateChecker);
        handler.post(dateChecker);

        startPlanetsListener();
    }

    @Override
    protected void onPause() {

        CourtRepository.removeObserver(courtObserver);

        handler.removeCallbacks(dateChecker);
        handler.removeCallbacks(blackholeLabelTicker);

        super.onPause();

        stopPlanetsListener();
    }

    // 뒤로가기 시 메뉴가 열려 있으면 닫기
    @Override
    public void onBackPressed() {

        if (menuOpen) {
            setMenuOpen(false);
        } else {
            super.onBackPressed();
        }
    }

    // ====================================
    // 행성 Firestore 실시간 조회
    // ====================================

    private void startPlanetsListener() {

        final int generation =
                ++planetsListenerGeneration;

        FirebaseUser user =
                FirebaseAuth.getInstance()
                        .getCurrentUser();

        if (user == null) {
            return;
        }

        FirebaseFirestore db =
                FirebaseFirestore.getInstance();

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

                    planetsListener =
                            db.collection("galaxies")
                                    .document(galaxyId)
                                    .collection("planets")
                                    .orderBy(
                                            "createdAt",
                                            Query.Direction.ASCENDING
                                    )
                                    .limit(24)
                                    .addSnapshotListener(
                                            (snapshot, error) -> {

                                                if (generation
                                                        != planetsListenerGeneration) {
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
                                                            doc.getString(
                                                                    "planetAsset"
                                                            );

                                                    assets.add(
                                                            asset == null
                                                                    ? "planet_01"
                                                                    : asset
                                                    );
                                                }

                                                galaxyView.setPlanetDocuments(
                                                        ids,
                                                        assets
                                                );
                                            }
                                    );
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

    // ====================================
    // 로그아웃
    // ====================================

    private void confirmLogout() {

        new android.app.AlertDialog.Builder(this)
                .setTitle("로그아웃")
                .setMessage("정말 로그아웃하시겠습니까?")
                .setNegativeButton("취소", null)
                .setPositiveButton(
                        "로그아웃",
                        (dialog, which) -> {

                            FirebaseAuth.getInstance()
                                    .signOut();

                            Navigator.toLogin(this);
                        }
                )
                .show();
    }

    // ====================================
    // 커플 연동 상태 실시간 감시
    // ====================================

    private void startConnectionWatcher() {

        if (connectionListener != null) {
            return;
        }

        FirebaseUser user =
                FirebaseAuth.getInstance()
                        .getCurrentUser();

        if (user == null) {
            Navigator.toLogin(this);
            return;
        }

        connectionListener =
                FirebaseFirestore.getInstance()
                        .collection("users")
                        .document(user.getUid())
                        .addSnapshotListener(
                                (snapshot, error) -> {

                                    if (error != null
                                            || snapshot == null
                                            || !snapshot.exists()) {
                                        return;
                                    }

                                    if (snapshot.getMetadata()
                                            .hasPendingWrites()) {
                                        return;
                                    }

                                    String galaxyId =
                                            snapshot.getString(
                                                    "galaxyId"
                                            );

                                    if (galaxyId == null) {

                                        stopGalaxyInfoWatcher();
                                        stopConnectionWatcher();

                                        FirebaseAuth.getInstance()
                                                .signOut();

                                        Navigator.toLogin(this);
                                        return;
                                    }

                                    // 은하계 이름과 날짜 실시간 감시
                                    startGalaxyInfoWatcher(galaxyId);
                                }
                        );
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

        stopGalaxyInfoWatcher();
        stopConnectionWatcher();

        super.onStop();
    }
}

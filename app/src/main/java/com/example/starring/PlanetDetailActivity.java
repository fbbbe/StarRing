
package com.example.starring;

import android.app.Activity;

import com.example.starring.common.BlackholeLock;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.widget.ImageButton;
import android.graphics.Bitmap;
import android.view.ViewOutlineProvider;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.text.InputFilter;
import android.content.Intent;

import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;


public class PlanetDetailActivity extends Activity {

    public static final String EXTRA_PLANET_ID = "planet_id";
    public static final String EXTRA_BROWSE_MODE = "browse_mode";
    public static final String EXTRA_BROWSE_PATHS = "browse_planet_paths";

    private static final int MAX_CAPTION = 300;

    // 화면 색상 (행성 생성 화면과 같은 연한 라벤더 계열)
    private static final int C_CARD = 0xF7FFFFFF;
    private static final int C_LINE = 0xFFE7E0F8;
    private static final int C_TEXT = 0xFF3F3A5A;
    private static final int C_SUB = 0xFF8F89A8;
    private static final int C_ACCENT = 0xFF7F6BE0;
    private static final int C_ACCENT_SOFT = 0xFFEFEAFF;
    private static final int C_ACCENT_LINE = 0xFFD3C9F5;
    private static final int C_BG = 0xFFF8F5FF;

    private final FirebaseFirestore db =
            FirebaseFirestore.getInstance();

    private final ArrayList<PhotoItem> photos =
            new ArrayList<>();

    private DocumentReference planetRef;
    private ListenerRegistration planetListener;
    private ListenerRegistration likesListener;
    private boolean browseMode;
    private boolean browseAnimating;
    private float browseDownX, browseDownY;
    private final ArrayList<String> browsePaths = new ArrayList<>();
    private TextView btnLike;
    private TextView btnPublish;
    private TextView browsePrev, browseNext;
    private TextView topTitle;
    private int currentThemeIndex;
    private int likeCount;
    private boolean liked;
    private boolean likeBusy;
    private boolean publicPlanet;
    private FrameLayout planetFrame;

    private ImageView previewImage;
    private TextView textCaption;
    private TextView textDate;
    private TextView textCount;
    private TextView btnEdit;

    private RecyclerView photoGrid;
    private PhotoAdapter adapter;

    private int selectedIndex = -1;
    private boolean saving = false;

    // ----- 갤러리 / 행성 두 화면 -----
    private LinearLayout galleryPage;
    private NestedScrollView galleryScroll;
    private LinearLayout planetPage;
    private TextView tabGallery;
    private TextView tabPlanet;

    private PlanetWorldView planetView;
    private TextView textYear;
    private ImageView infoThumb;
    private TextView infoTitle;
    private TextView infoSub;
    private TextView infoOpen;

    // 지금 보고 있는 행성과, 은하계의 행성 목록 (달 순서) - ‹ › 이동에 사용
    private String galaxyId;
    private String currentPlanetId;
    private final ArrayList<String> planetIds = new ArrayList<>();
    private TextView planetPrev;
    private TextView planetNext;

    // 이 행성이 속한 달 ("2026-11"). 행성 하나 = 한 달
    private String planetMonthKey = "";
    private String selectedDate = "";
    private PlanetWorldView.Pin selectedPin;

    private final HashMap<String, Bitmap> thumbCache = new HashMap<>();
    private final ExecutorService thumbExecutor =
            Executors.newFixedThreadPool(2);

    private static class PhotoItem {

        final String url;
        final String publicId;
        final String caption;
        final String date;

        PhotoItem(
                String url,
                String publicId,
                String caption,
                String date) {

            this.url = url;
            this.publicId = publicId;
            this.caption = caption;
            this.date = date;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SpaceBackdrop.applyLightWindow(this);

        browseMode = getIntent().getBooleanExtra(EXTRA_BROWSE_MODE, false);
        if (browseMode) {
            ArrayList<String> paths = getIntent().getStringArrayListExtra(EXTRA_BROWSE_PATHS);
            if (paths != null) browsePaths.addAll(paths);
            // [sh] 블랙홀 잠금: 둘러보기는 블랙홀 해결 전까지 막음 (데이터가 늦게 와도 닫힘)
            BlackholeLock.guardScreen(this);
        }
        createScreen();

        if (browseMode) {
            if (browsePaths.isEmpty()) {
                message("둘러볼 행성이 없습니다.");
                finish();
                return;
            }
            switchBrowsePlanet(browsePaths.get(0));
        } else {
            String planetId = getIntent().getStringExtra(EXTRA_PLANET_ID);
            if (planetId == null || planetId.isEmpty()) {
                finish();
                return;
            }
            currentPlanetId = planetId;
            loadPlanetReference(planetId);
        }
    }

    // ==========================================
    // 화면 구성
    // ==========================================

    private void createScreen() {

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        // 상태바/내비게이션 바 영역을 피해서 배치 (우주 재판장과 동일)
        root.setFitsSystemWindows(true);

        // 상단: 우주 재판장 상단바와 같은 크기/배치
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(8), 0, dp(16), 0);

        ImageButton back = new ImageButton(this);
        back.setImageResource(R.drawable.ic_court_back);
        back.setColorFilter(C_TEXT);
        back.setContentDescription("뒤로가기");

        TypedValue ripple = new TypedValue();
        getTheme().resolveAttribute(
                android.R.attr.selectableItemBackgroundBorderless,
                ripple,
                true
        );
        back.setBackgroundResource(ripple.resourceId);

        top.addView(
                back,
                new LinearLayout.LayoutParams(
                        dp(48), dp(48)
                )
        );

        back.setOnClickListener(v -> finish());

        topTitle = makeText(
                browseMode ? "행성 둘러보기" : "행성 상세", 20, C_TEXT, true
        );

        LinearLayout.LayoutParams titleParams =
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                );

        titleParams.leftMargin = dp(4);

        top.addView(topTitle, titleParams);

        root.addView(
                top,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(56)
                )
        );

        // 탭: 갤러리 / 행성
        LinearLayout tabs = new LinearLayout(this);
        tabs.setGravity(Gravity.CENTER);
        tabs.setPadding(dp(3), dp(3), dp(3), dp(3));
        tabs.setBackground(rounded(C_CARD, C_LINE, 1, 22));

        tabGallery = makeText("갤러리", 14, C_TEXT, true);
        tabPlanet = makeText("행성", 14, C_TEXT, true);

        tabGallery.setGravity(Gravity.CENTER);
        tabPlanet.setGravity(Gravity.CENTER);

        // 행성 탭이 왼쪽(먼저), 갤러리 탭이 오른쪽
        tabs.addView(
                tabPlanet,
                new LinearLayout.LayoutParams(0, dp(36), 1f)
        );

        tabs.addView(
                tabGallery,
                new LinearLayout.LayoutParams(0, dp(36), 1f)
        );

        LinearLayout.LayoutParams tabsParams =
                new LinearLayout.LayoutParams(-1, -2);

        tabsParams.setMargins(dp(18), dp(4), dp(18), dp(6));

        root.addView(tabs, tabsParams);

        tabGallery.setOnClickListener(v -> showPage(false));
        tabPlanet.setOnClickListener(v -> showPage(true));

        // 갤러리 화면 (지금까지의 화면 그대로)
        galleryPage = new LinearLayout(this);
        galleryPage.setOrientation(LinearLayout.VERTICAL);

        // 위의 탭(행성/갤러리)만 고정하고, 나머지는 함께 스크롤
        galleryScroll = new NestedScrollView(this);

        galleryScroll.addView(
                galleryPage,
                new FrameLayout.LayoutParams(-1, -2)
        );

        root.addView(
                galleryScroll,
                new LinearLayout.LayoutParams(-1, 0, 1f)
        );

        // 사진 확대 미리보기
        previewImage = new ImageView(this);

        previewImage.setScaleType(
                ImageView.ScaleType.FIT_CENTER
        );

        previewImage.setBackground(
                rounded(C_CARD, C_LINE, 1, 24)
        );

        previewImage.setPadding(dp(8), dp(8), dp(8), dp(8));

        LinearLayout.LayoutParams previewParams =
                new LinearLayout.LayoutParams(
                        -1, dp(245)
                );

        previewParams.setMargins(
                dp(18), dp(18),
                dp(18), dp(12)
        );

        galleryPage.addView(previewImage, previewParams);

        // 설명
        textCaption = makeText(
                "사진을 불러오는 중...",
                16,
                C_TEXT,
                false
        );

        textCaption.setPadding(
                dp(22), dp(8), dp(22), dp(8)
        );

        galleryPage.addView(textCaption);

        // 날짜
        textDate = makeText(
                "", 13, C_SUB, false
        );

        textDate.setPadding(
                dp(22), dp(3), dp(22), dp(10)
        );

        galleryPage.addView(textDate);

        // 사진 정보 수정 버튼
        btnEdit = makeText(
                "사진 설명 · 날짜 수정",
                15,
                Color.WHITE,
                true
        );

        btnEdit.setGravity(Gravity.CENTER);

        GradientDrawable editBg = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xFF9C8CF0, 0xFFB79BF2}
        );
        editBg.setCornerRadius(dp(26));
        btnEdit.setBackground(editBg);

        LinearLayout.LayoutParams editParams =
                new LinearLayout.LayoutParams(
                        -1, dp(50)
                );

        editParams.setMargins(
                dp(22), dp(4),
                dp(22), dp(12)
        );

        galleryPage.addView(btnEdit, editParams);
        if (browseMode) btnEdit.setVisibility(View.GONE);

        // 기존 행성에 사진 추가
        TextView btnAddPhotos = makeText(
                "＋ 사진 추가",
                15,
                C_ACCENT,
                true
        );

        btnAddPhotos.setGravity(Gravity.CENTER);

        btnAddPhotos.setBackground(
                rounded(C_ACCENT_SOFT, C_ACCENT_LINE, 1, 26)
        );

        LinearLayout.LayoutParams addParams =
                new LinearLayout.LayoutParams(
                        -1,
                        dp(50)
                );

        addParams.setMargins(
                dp(22), 0,
                dp(22), dp(12)
        );

        galleryPage.addView(btnAddPhotos, addParams);
        if (browseMode) btnAddPhotos.setVisibility(View.GONE);

        btnAddPhotos.setOnClickListener(v -> {

            if (saving) {
                return;
            }

            // [sh] 블랙홀 잠금: 보기만 허용, 사진 추가 막음
            if (BlackholeLock.blockIfLocked(this)) return;

            String planetId = currentPlanetId;

            if (planetId == null || planetId.isEmpty()) {
                message("행성 정보를 찾을 수 없습니다.");
                return;
            }

            Intent intent = new Intent(
                    PlanetDetailActivity.this,
                    PlanetCreateActivity.class
            );

            // 새 행성을 만들지 않고 기존 행성에 사진 추가
            intent.putExtra(
                    PlanetCreateActivity.EXTRA_PLANET_ID,
                    planetId
            );

            // 이 행성의 달 안에서만 날짜를 고르도록 전달
            intent.putExtra(
                    PlanetCreateActivity.EXTRA_MONTH_KEY,
                    planetMonthKey
            );

            startActivity(intent);
        });


        btnEdit.setEnabled(false);

        btnEdit.setOnClickListener(v -> openEditDialog());

        // 사진 개수
        textCount = makeText(
                "사진 불러오는 중...",
                15,
                C_ACCENT,
                true
        );

        textCount.setPadding(
                dp(22), dp(10),
                dp(22), dp(10)
        );

        galleryPage.addView(textCount);

        // 사진 그리드
        photoGrid = new RecyclerView(this);

        GridLayoutManager gridManager =
                new GridLayoutManager(this, 3);

        // 날짜 구분선(헤더)은 한 줄 전체를 차지
        gridManager.setSpanSizeLookup(
                new GridLayoutManager.SpanSizeLookup() {

                    @Override
                    public int getSpanSize(int position) {

                        return adapter != null
                                && adapter.isHeader(position)
                                ? 3
                                : 1;
                    }
                }
        );

        photoGrid.setLayoutManager(gridManager);

        photoGrid.setPadding(
                dp(12), dp(4),
                dp(12), dp(12)
        );

        photoGrid.setClipToPadding(false);

        adapter = new PhotoAdapter();
        photoGrid.setAdapter(adapter);

        // 스크롤은 바깥(galleryScroll)이 담당
        photoGrid.setNestedScrollingEnabled(false);

        galleryPage.addView(
                photoGrid,
                new LinearLayout.LayoutParams(
                        -1, -2
                )
        );

        // 행성 화면 (월별 사진이 핀으로 꽂힌 행성)
        planetPage = new LinearLayout(this);
        planetPage.setOrientation(LinearLayout.VERTICAL);
        planetPage.setVisibility(View.GONE);

        root.addView(
                planetPage,
                new LinearLayout.LayoutParams(-1, 0, 1f)
        );

        // 달 이동:  ‹  2026년 11월  ›  (달마다 있는 행성끼리 이동)
        LinearLayout monthBar = new LinearLayout(this);
        monthBar.setGravity(Gravity.CENTER);

        planetPrev = makeText("‹", 26, C_ACCENT, true);
        planetNext = makeText("›", 26, C_ACCENT, true);

        planetPrev.setGravity(Gravity.CENTER);
        planetNext.setGravity(Gravity.CENTER);

        textYear = makeText("", 18, C_TEXT, true);
        textYear.setGravity(Gravity.CENTER);

        monthBar.addView(
                planetPrev,
                new LinearLayout.LayoutParams(dp(56), dp(44))
        );

        monthBar.addView(
                textYear,
                new LinearLayout.LayoutParams(dp(150), dp(44))
        );

        monthBar.addView(
                planetNext,
                new LinearLayout.LayoutParams(dp(56), dp(44))
        );

        planetPage.addView(
                monthBar,
                new LinearLayout.LayoutParams(-1, -2)
        );
        if (browseMode) monthBar.setVisibility(View.GONE);

        planetPrev.setOnClickListener(v -> movePlanet(-1));
        planetNext.setOnClickListener(v -> movePlanet(1));

        planetView = new PlanetWorldView(this);

        // 행성 위에 "테마" 버튼을 얹기 위해 FrameLayout 으로 감쌈
        planetFrame = new FrameLayout(this);

        planetFrame.addView(
                planetView,
                new FrameLayout.LayoutParams(-1, -1)
        );

        TextView btnTheme = makeText("테마", 13, C_ACCENT, true);
        btnTheme.setGravity(Gravity.CENTER);
        btnTheme.setPadding(dp(14), 0, dp(14), 0);
        btnTheme.setBackground(rounded(C_CARD, C_LINE, 1, 18));

        FrameLayout.LayoutParams themeParams =
                new FrameLayout.LayoutParams(-2, dp(34));

        themeParams.gravity = Gravity.TOP | Gravity.END;
        themeParams.setMargins(0, dp(6), dp(16), 0);

        planetFrame.addView(btnTheme, themeParams);
        if (browseMode) btnTheme.setVisibility(View.GONE);

        if (browseMode) {
            // 행성 한가운데 좌우 원형 버튼 (인스타 가로 넘김처럼 이동)
            browsePrev = addBrowseArrow(planetFrame, "‹", Gravity.START);
            browseNext = addBrowseArrow(planetFrame, "›", Gravity.END);
            browsePrev.setOnClickListener(v -> movePlanet(-1));
            browseNext.setOnClickListener(v -> movePlanet(1));
            updateBrowseArrows();
        }

        btnTheme.setOnClickListener(v -> showThemePicker());

        planetPage.addView(
                planetFrame,
                new LinearLayout.LayoutParams(-1, 0, 1f)
        );

        planetView.setOnPinClickListener(this::onPinClicked);

        // 핀을 누르면 나오는 안내 카드
        LinearLayout info = new LinearLayout(this);
        info.setGravity(Gravity.CENTER_VERTICAL);
        info.setPadding(dp(12), dp(12), dp(16), dp(12));
        info.setBackground(rounded(C_CARD, C_LINE, 1, 22));

        infoThumb = new ImageView(this);
        infoThumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        infoThumb.setBackground(rounded(C_ACCENT_SOFT, 0, 0, 14));
        infoThumb.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        infoThumb.setClipToOutline(true);
        infoThumb.setVisibility(View.GONE);

        LinearLayout.LayoutParams thumbParams =
                new LinearLayout.LayoutParams(dp(56), dp(56));

        thumbParams.rightMargin = dp(12);

        info.addView(infoThumb, thumbParams);

        LinearLayout infoText = new LinearLayout(this);
        infoText.setOrientation(LinearLayout.VERTICAL);

        infoTitle = makeText("핀을 눌러보세요", 15, C_TEXT, true);
        infoSub = makeText(
                "날짜마다 모은 추억이 핀으로 꽂혀 있어요.",
                12, C_SUB, false
        );

        infoText.addView(infoTitle);
        infoText.addView(infoSub);

        info.addView(
                infoText,
                new LinearLayout.LayoutParams(0, -2, 1f)
        );

        infoOpen = makeText("보기 ›", 14, C_ACCENT, true);
        infoOpen.setVisibility(View.GONE);

        info.addView(infoOpen);

        LinearLayout.LayoutParams infoParams =
                new LinearLayout.LayoutParams(-1, -2);

        infoParams.setMargins(dp(18), dp(4), dp(18), dp(16));

        // 핀 안내 상자 위: 공개 설정(내 행성) / 좋아요(모두)
        LinearLayout reactionRow = new LinearLayout(this);
        reactionRow.setGravity(Gravity.CENTER_VERTICAL);
        reactionRow.setPadding(dp(18), dp(4), dp(18), dp(2));

        btnPublish = makeText("비공개", 13, C_ACCENT, true);
        btnPublish.setGravity(Gravity.CENTER);
        btnPublish.setPadding(dp(12), 0, dp(12), 0);
        btnPublish.setBackground(rounded(C_ACCENT_SOFT, C_ACCENT_LINE, 1, 18));
        btnPublish.setOnClickListener(v -> togglePublication());
        if (browseMode) btnPublish.setVisibility(View.GONE);
        reactionRow.addView(btnPublish, new LinearLayout.LayoutParams(-2, dp(36)));

        reactionRow.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));

        btnLike = makeText("♡ 0", 17, 0xFFE06C93, true);
        btnLike.setGravity(Gravity.CENTER);
        btnLike.setPadding(dp(12), 0, dp(12), 0);
        btnLike.setBackground(rounded(C_CARD, C_LINE, 1, 18));
        btnLike.setOnClickListener(v -> toggleLike());
        reactionRow.addView(btnLike, new LinearLayout.LayoutParams(-2, dp(38)));

        planetPage.addView(reactionRow, new LinearLayout.LayoutParams(-1, dp(44)));
        planetPage.addView(info, infoParams);

        infoOpen.setOnClickListener(v -> {
            if (selectedPin != null) {
                openInGallery(selectedPin.photoIndex);
            }
        });

        // 처음에는 행성 화면이 먼저 보임
        showPage(true);

        // 배경(우주 느낌) 위에 화면을 올림
        FrameLayout frame = new FrameLayout(this);

        frame.addView(
                new SpaceBackdrop(this),
                new FrameLayout.LayoutParams(-1, -1)
        );

        frame.addView(
                root,
                new FrameLayout.LayoutParams(-1, -1)
        );

        setContentView(frame);
    }

    // ==========================================
    // 갤러리 / 행성 화면 전환
    // ==========================================

    private void showPage(boolean planet) {

        galleryScroll.setVisibility(planet ? View.GONE : View.VISIBLE);
        planetPage.setVisibility(planet ? View.VISIBLE : View.GONE);

        styleTab(tabGallery, !planet);
        styleTab(tabPlanet, planet);
    }

    private void styleTab(TextView tab, boolean active) {

        tab.setTextColor(active ? Color.WHITE : C_SUB);

        if (active) {

            GradientDrawable bg = new GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{0xFF9C8CF0, 0xFFB79BF2}
            );
            bg.setCornerRadius(dp(19));
            tab.setBackground(bg);

        } else {
            tab.setBackground(null);
        }
    }

    // ==========================================
    // 행성 화면: 월별 핀
    // ==========================================

    /** 사진 날짜가 yyyy-MM-dd 형식인지 */
    private boolean validDate(String date) {
        return date != null && date.matches("\\d{4}-\\d{2}-\\d{2}");
    }

    private void refreshPlanetPage() {

        if (planetView == null) return;

        // 이 행성에 골라 둔 테마 적용
        planetView.setTheme(currentThemeIndex);

        textYear.setText(PlanetMonth.label(planetMonthKey));

        // 날짜별로 사진을 모음 (날짜 순서대로)
        TreeMap<String, ArrayList<Integer>> byDate = new TreeMap<>();

        for (int i = 0; i < photos.size(); i++) {

            String date = photos.get(i).date;

            if (!validDate(date)) continue;

            ArrayList<Integer> list = byDate.get(date);

            if (list == null) {
                list = new ArrayList<>();
                byDate.put(date, list);
            }

            list.add(i);
        }

        ArrayList<PlanetWorldView.Pin> pins = new ArrayList<>();
        PlanetWorldView.Pin keep = null;

        for (String date : byDate.keySet()) {

            ArrayList<Integer> list = byDate.get(date);

            int first = list.get(0);
            PhotoItem rep = photos.get(first);

            PlanetWorldView.Pin pin = new PlanetWorldView.Pin(
                    date,
                    Integer.parseInt(date.substring(5, 7)),
                    Integer.parseInt(date.substring(8, 10)),
                    list.size(),
                    first,
                    rep.url
            );

            Bitmap cached = thumbCache.get(rep.url);

            if (cached != null) {
                planetView.setPinThumb(pin, cached);
            } else {
                loadThumb(pin);
            }

            if (date.equals(selectedDate)) keep = pin;

            pins.add(pin);
        }

        planetView.setPins(pins);

        selectedPin = keep;

        if (keep == null) selectedDate = "";

        planetView.setSelectedDate(selectedDate);
        bindInfo();
    }

    private void loadThumb(PlanetWorldView.Pin pin) {

        final String url = pin.url;

        thumbExecutor.execute(() -> {

            try {

                Bitmap bitmap = Glide.with(getApplicationContext())
                        .asBitmap()
                        .load(url)
                        .centerCrop()
                        .submit(160, 160)
                        .get();

                thumbCache.put(url, bitmap);

                runOnUiThread(() -> {

                    if (!isDestroyed()) {
                        planetView.setPinThumb(pin, bitmap);
                    }
                });

            } catch (Exception ignored) {
                // 사진을 못 받으면 반짝이 핀으로 그대로 둠
            }
        });
    }

    private void onPinClicked(PlanetWorldView.Pin pin) {

        selectedPin = pin;
        selectedDate = pin.date;

        planetView.setSelectedDate(selectedDate);
        bindInfo();
    }

    private void bindInfo() {

        if (selectedPin == null) {

            infoThumb.setVisibility(View.GONE);
            infoOpen.setVisibility(View.GONE);

            boolean empty = photos.isEmpty();

            infoTitle.setText(
                    empty ? "아직 사진이 없어요" : "핀을 눌러보세요"
            );

            infoSub.setText(
                    empty
                            ? "사진을 추가하면 날짜마다 핀이 꽂혀요."
                            : "날짜마다 모은 추억이 핀으로 꽂혀 있어요."
            );

            return;
        }

        PhotoItem rep = photos.get(selectedPin.photoIndex);

        infoTitle.setText(
                selectedPin.month + "월 " + selectedPin.day + "일 · 사진 "
                        + selectedPin.count + "장"
        );

        infoSub.setText(
                rep.caption.isEmpty()
                        ? "탭해서 이 날의 사진을 볼 수 있어요."
                        : rep.caption
        );

        infoSub.setMaxLines(1);
        infoSub.setEllipsize(android.text.TextUtils.TruncateAt.END);

        infoThumb.setVisibility(View.VISIBLE);
        infoOpen.setVisibility(View.VISIBLE);

        Glide.with(this)
                .load(rep.url)
                .centerCrop()
                .into(infoThumb);
    }

    /** 행성 핀에서 갤러리로: 그 달의 대표 사진을 선택해서 보여줌 */
    private void openInGallery(int photoIndex) {

        showPage(false);

        if (photoIndex >= 0 && photoIndex < photos.size()) {
            selectPhoto(photoIndex);

            // 위쪽 미리보기에 고른 사진이 보이도록 맨 위로
            galleryScroll.post(() -> galleryScroll.scrollTo(0, 0));
        }
    }

    // ==========================================
    // 현재 은하계 및 행성 문서 확인
    // ==========================================

    private void loadPlanetReference(String planetId) {

        FirebaseUser user =
                FirebaseAuth.getInstance().getCurrentUser();

        if (user == null) {
            message("로그인이 필요합니다.");
            finish();
            return;
        }

        db.collection("users")
                .document(user.getUid())
                .get()
                .addOnSuccessListener(this, userDoc -> {

                    String galaxyId =
                            userDoc.getString("galaxyId");

                    if (galaxyId == null) {
                        message("연결된 은하계가 없습니다.");
                        finish();
                        return;
                    }

                    this.galaxyId = galaxyId;

                    planetRef = db.collection("galaxies")
                            .document(galaxyId)
                            .collection("planets")
                            .document(planetId);

                    startPlanetListener();
                    startLikesListener();
                    loadPlanetList();
                })
                .addOnFailureListener(this, error -> {

                    message(
                            "은하계 확인 실패: "
                                    + error.getMessage()
                    );
                });
    }

    // ==========================================
    // 행성 사진 실시간 조회
    // ==========================================

    // ==========================================
    // 달마다 있는 행성끼리 ‹ › 이동
    // ==========================================

    private void loadPlanetList() {

        if (galaxyId == null) return;

        db.collection("galaxies")
                .document(galaxyId)
                .collection("planets")
                .get()
                .addOnSuccessListener(this, snapshot -> {

                    ArrayList<String[]> list = new ArrayList<>();

                    for (DocumentSnapshot doc : snapshot.getDocuments()) {

                        String key = PlanetMonth.of(doc);

                        list.add(new String[]{
                                key == null ? "" : key,
                                doc.getId()
                        });
                    }

                    // 달 순서 (같은 달이면 ID 순)
                    Collections.sort(list, (a, b) -> {

                        int c = a[0].compareTo(b[0]);

                        return c != 0 ? c : a[1].compareTo(b[1]);
                    });

                    planetIds.clear();

                    for (String[] item : list) {
                        planetIds.add(item[1]);
                    }

                    updatePlanetArrows();
                });
    }

    private void updatePlanetArrows() {

        if (planetPrev == null) return;

        int index = planetIds.indexOf(currentPlanetId);

        planetPrev.setAlpha(index > 0 ? 1f : 0.3f);

        planetNext.setAlpha(
                index >= 0 && index < planetIds.size() - 1 ? 1f : 0.3f
        );
    }

    private void movePlanet(int step) {

        if (browseMode) {
            int index = planetRef == null ? -1 : browsePaths.indexOf(planetRef.getPath());
            int target = index + step;
            if (index < 0 || target < 0 || target >= browsePaths.size()) return;
            animateBrowseTo(browsePaths.get(target), step);
            return;
        }

        int index = planetIds.indexOf(currentPlanetId);
        if (index < 0) return;
        int target = index + step;
        if (target < 0 || target >= planetIds.size()) return;
        switchPlanet(planetIds.get(target));
    }

    // 화살표 클릭 또는 좌우 스와이프 시 행성이 가로로 넘어가는 연출
    private void animateBrowseTo(String path, int step) {
        if (browseAnimating || planetView == null) return;
        browseAnimating = true;
        planetView.animate().cancel();
        planetView.animate()
                .translationX(-step * dp(70))
                .alpha(0.20f)
                .setDuration(140)
                .withEndAction(() -> {
                    switchBrowsePlanet(path);
                    planetView.setTranslationX(step * dp(70));
                    planetView.animate()
                            .translationX(0f)
                            .alpha(1f)
                            .setDuration(170)
                            .withEndAction(() -> browseAnimating = false)
                            .start();
                }).start();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (browseMode && planetPage != null
                && planetPage.getVisibility() == View.VISIBLE) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    browseDownX = event.getX();
                    browseDownY = event.getY();
                    break;
                case MotionEvent.ACTION_UP:
                    float dx = event.getX() - browseDownX;
                    float dy = event.getY() - browseDownY;
                    if (Math.abs(dx) > dp(80)
                            && Math.abs(dx) > Math.abs(dy) * 1.4f) {
                        movePlanet(dx > 0 ? -1 : 1);
                        return true;
                    }
                    break;
            }
        }
        return super.dispatchTouchEvent(event);
    }

    private void switchBrowsePlanet(String path) {
        // 정상적인 galaxies/{galaxyId}/planets/{planetId} 경로만 사용
        String[] parts = path.split("/");
        if (parts.length != 4 || !"galaxies".equals(parts[0])
                || !"planets".equals(parts[2])) {
            message("올바르지 않은 행성 경로입니다.");
            finish();
            return;
        }

        if (planetListener != null) { planetListener.remove(); planetListener = null; }
        stopLikesListener();
        galaxyId = parts[1];
        currentPlanetId = parts[3];
        planetRef = db.document(path);
        photos.clear();
        selectedIndex = -1;
        selectedDate = "";
        selectedPin = null;
        currentThemeIndex = 0;
        adapter.reload();
        planetView.setPins(new ArrayList<>());
        topTitle.setText("행성 둘러보기");
        updateBrowseArrows();
        startPlanetListener();
        startLikesListener();
    }

    private TextView addBrowseArrow(FrameLayout frame, String arrow, int side) {
        TextView button = makeText(arrow, 28, C_ACCENT, true);
        button.setGravity(Gravity.CENTER);
        button.setBackground(rounded(C_CARD, C_LINE, 1, 40));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(42), dp(42));
        lp.gravity = Gravity.CENTER_VERTICAL | side;
        if (side == Gravity.START) lp.leftMargin = dp(8);
        else lp.rightMargin = dp(8);
        frame.addView(button, lp);
        return button;
    }

    private void updateBrowseArrows() {
        if (!browseMode || browsePrev == null || browseNext == null) return;
        int index = planetRef == null ? -1 : browsePaths.indexOf(planetRef.getPath());
        browsePrev.setAlpha(index > 0 ? 1f : 0.25f);
        browseNext.setAlpha(index >= 0 && index < browsePaths.size() - 1 ? 1f : 0.25f);
    }

    private void switchPlanet(String planetId) {

        if (planetListener != null) {
            planetListener.remove();
            planetListener = null;
        }

        currentPlanetId = planetId;
        stopLikesListener();

        photos.clear();
        selectedIndex = -1;
        selectedDate = "";
        selectedPin = null;

        adapter.reload();
        planetView.setPins(new ArrayList<>());

        planetRef = db.collection("galaxies")
                .document(galaxyId)
                .collection("planets")
                .document(planetId);

        updatePlanetArrows();
        startPlanetListener();
        startLikesListener();
    }

    private void startPlanetListener() {

        if (planetRef == null || planetListener != null) {
            return;
        }

        planetListener = planetRef.addSnapshotListener(
                (snapshot, error) -> {

                    if (error != null) {
                        message(
                                "행성 조회 실패: "
                                        + error.getMessage()
                        );
                        return;
                    }

                    if (snapshot == null) return;

                    if (!snapshot.exists()) {
                        message("행성을 찾을 수 없습니다.");
                        finish();
                        return;
                    }

                    if (browseMode && !Boolean.TRUE.equals(snapshot.getBoolean("isPublic"))) {
                        message("비공개로 전환된 행성입니다.");
                        finish();
                        return;
                    }
                    publicPlanet = Boolean.TRUE.equals(snapshot.getBoolean("isPublic"));
                    if (browseMode) {
                        String name = snapshot.getString("publicGalaxyName");
                        topTitle.setText((name == null || name.trim().isEmpty())
                                ? "다른 은하계의 행성" : name + " 은하계");
                    }
                    if (btnPublish != null) btnPublish.setText(publicPlanet ? "공개 중" : "비공개");
                    Long idx = snapshot.getLong("themeIndex");
                    currentThemeIndex = idx != null
                            ? (int) (long) idx
                            : (browseMode ? 0 : loadTheme(currentPlanetId));
                    updatePhotos(snapshot);
                }
        );
    }

    private void updatePhotos(DocumentSnapshot snapshot) {

        String previousPublicId = null;

        if (selectedIndex >= 0
                && selectedIndex < photos.size()) {

            previousPublicId =
                    photos.get(selectedIndex).publicId;
        }

        // 이 행성이 속한 달 (문서 ID 가 달 이름이면 그것, 예전 행성은 만든 달)
        String month = PlanetMonth.of(snapshot);

        if (month == null) {
            month = PlanetMonth.key(
                    Calendar.getInstance().get(Calendar.YEAR),
                    Calendar.getInstance().get(Calendar.MONTH) + 1
            );
        }

        planetMonthKey = month;

        photos.clear();

        Object value = snapshot.get("photos");

        if (value instanceof List) {

            for (Object element : (List<?>) value) {

                if (!(element instanceof Map)) continue;

                Map<?, ?> data = (Map<?, ?>) element;

                String url = readString(data, "imageUrl");

                if (url.isEmpty()) continue;

                photos.add(new PhotoItem(
                        url,
                        readString(data, "publicId"),
                        readString(data, "caption"),
                        readString(data, "date")
                ));
            }
        }

        selectedIndex = -1;

        if (!photos.isEmpty()) {

            if (previousPublicId != null
                    && !previousPublicId.isEmpty()) {

                for (int i = 0; i < photos.size(); i++) {

                    if (previousPublicId.equals(
                            photos.get(i).publicId)) {

                        selectedIndex = i;
                        break;
                    }
                }
            }

            if (selectedIndex == -1) {
                selectedIndex = 0;
            }
        }

        adapter.reload();
        renderSelectedPhoto();
        refreshPlanetPage();
    }

    private String readString(Map<?, ?> data, String key) {

        Object value = data.get(key);

        return value instanceof String
                ? (String) value
                : "";
    }

    // ==========================================
    // 사진 선택 및 표시
    // ==========================================

    private void selectPhoto(int index) {

        if (index < 0 || index >= photos.size()) {
            return;
        }

        int previous = selectedIndex;

        selectedIndex = index;

        if (previous >= 0) {
            adapter.notifyItemChanged(adapter.rowOfPhoto(previous));
        }

        adapter.notifyItemChanged(adapter.rowOfPhoto(index));

        renderSelectedPhoto();
    }

    private void renderSelectedPhoto() {

        btnEdit.setEnabled(
                selectedIndex >= 0 && !saving
        );

        if (selectedIndex < 0
                || selectedIndex >= photos.size()) {

            previewImage.setImageDrawable(null);

            textCaption.setText(
                    "등록된 사진이 없습니다."
            );

            textDate.setText("");

            textCount.setText("추억 사진 0장");
            return;
        }

        PhotoItem item = photos.get(selectedIndex);

        Glide.with(this)
                .load(item.url)
                .fitCenter()
                .into(previewImage);

        textCaption.setText(
                item.caption
        );

        textDate.setText(
                item.date.isEmpty()
                        ? "날짜 정보 없음"
                        : item.date
        );

        textCount.setText(
                "추억 사진 " + photos.size()
                        + "장 · "
                        + (selectedIndex + 1)
                        + "번째 사진"
        );
    }

    // ==========================================
    // 사진 정보 수정 창
    // ==========================================

    private void openEditDialog() {

        // [sh] 블랙홀 잠금: 보기만 허용, 사진 편집 막음
        if (BlackholeLock.blockIfLocked(this)) return;

        if (saving
                || selectedIndex < 0
                || selectedIndex >= photos.size()) {
            return;
        }

        PhotoItem item = photos.get(selectedIndex);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);

        content.setPadding(
                dp(22), dp(12),
                dp(22), dp(8)
        );

        TextView captionLabel = makeText(
                "사진 설명",
                15,
                C_TEXT,
                true
        );

        content.addView(captionLabel);

        EditText editCaption = new EditText(this);

        editCaption.setText(item.caption);
        editCaption.setHint("사진 설명을 입력해주세요.");
        editCaption.setTextSize(16);
        editCaption.setMinLines(2);
        editCaption.setMaxLines(4);

        editCaption.setFilters(
                new InputFilter[]{
                        new InputFilter.LengthFilter(
                                MAX_CAPTION
                        )
                }
        );

        content.addView(editCaption);

        TextView dateLabel = makeText(
                "사진 날짜",
                15,
                C_TEXT,
                true
        );

        LinearLayout.LayoutParams labelParams =
                new LinearLayout.LayoutParams(-1, -2);

        labelParams.topMargin = dp(18);

        content.addView(dateLabel, labelParams);

        final String[] selectedDate = {
                item.date
        };

        TextView dateButton = makeText(
                item.date,
                16,
                C_ACCENT,
                true
        );

        dateButton.setPadding(
                dp(12), dp(16),
                dp(12), dp(16)
        );

        dateButton.setBackground(
                rounded(C_BG, C_LINE, 1, 14)
        );

        LinearLayout.LayoutParams dateParams =
                new LinearLayout.LayoutParams(
                        -1, -2
                );

        dateParams.topMargin = dp(8);

        content.addView(dateButton, dateParams);

        dateButton.setOnClickListener(v -> {

            Calendar calendar = Calendar.getInstance();

            try {

                String[] parts =
                        selectedDate[0].split("-");

                calendar.set(
                        Integer.parseInt(parts[0]),
                        Integer.parseInt(parts[1]) - 1,
                        Integer.parseInt(parts[2])
                );

            } catch (Exception ignored) {
                // 날짜가 없으면 오늘 날짜를 표시
            }

            DatePickerDialog picker =
                    new DatePickerDialog(
                            this,
                            R.style.StarRing_LightDialog,
                            (view, year, month, day) -> {

                                selectedDate[0] =
                                        String.format(
                                                Locale.US,
                                                "%04d-%02d-%02d",
                                                year,
                                                month + 1,
                                                day
                                        );

                                dateButton.setText(
                                        selectedDate[0]
                                );
                            },
                            calendar.get(Calendar.YEAR),
                            calendar.get(Calendar.MONTH),
                            calendar.get(Calendar.DAY_OF_MONTH)
                    );

            // 이 행성의 달 안에서만 선택 (미래 날짜는 제외)
            if (PlanetMonth.isMonthKey(planetMonthKey)) {

                int py = Integer.parseInt(
                        planetMonthKey.substring(0, 4));
                int pm = Integer.parseInt(
                        planetMonthKey.substring(5, 7)) - 1;

                Calendar first = Calendar.getInstance();
                first.clear();
                first.set(py, pm, 1, 0, 0, 0);

                Calendar last = Calendar.getInstance();
                last.clear();
                last.set(py, pm, 1, 23, 59, 59);
                last.set(
                        Calendar.DAY_OF_MONTH,
                        last.getActualMaximum(Calendar.DAY_OF_MONTH)
                );

                picker.getDatePicker().setMinDate(
                        first.getTimeInMillis());

                picker.getDatePicker().setMaxDate(Math.min(
                        last.getTimeInMillis(),
                        System.currentTimeMillis()));

            } else {

                picker.getDatePicker().setMaxDate(
                        System.currentTimeMillis()
                );
            }

            picker.show();
        });

        AlertDialog dialog =
                new AlertDialog.Builder(this, R.style.StarRing_LightDialog)
                        .setTitle("사진 정보 수정")
                        .setView(content)
                        .setNegativeButton("취소", null)
                        .setPositiveButton("저장", null)
                        .create();

        dialog.show();

        dialog.getButton(
                AlertDialog.BUTTON_POSITIVE
        ).setOnClickListener(v -> {

            String caption =
                    editCaption.getText()
                            .toString()
                            .trim();

            String date = selectedDate[0];

            if (caption.length() > MAX_CAPTION) {
                message("설명은 300자 이하로 입력해주세요.");
                return;
            }

            if (date == null || date.isEmpty()) {
                message("날짜를 선택해주세요.");
                return;
            }

            if (caption.equals(item.caption)
                    && date.equals(item.date)) {

                dialog.dismiss();
                return;
            }

            if (saving) return;

            saving = true;

            dialog.getButton(
                    AlertDialog.BUTTON_POSITIVE
            ).setEnabled(false);

            savePhotoChanges(
                    item,
                    caption,
                    date,
                    dialog
            );
        });
    }

    // ==========================================
    // 행성 테마 (이 폰에만 저장)
    // ==========================================

    private static final String THEME_PREFS = "starring_planet_theme";

    private int loadTheme(String planetId) {

        if (planetId == null) {
            return 0;
        }

        try {
            return getSharedPreferences(THEME_PREFS, MODE_PRIVATE)
                    .getInt(planetId, 0);
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private void saveTheme(String planetId, int index) {

        if (planetId == null) {
            return;
        }

        try {
            getSharedPreferences(THEME_PREFS, MODE_PRIVATE)
                    .edit()
                    .putInt(planetId, index)
                    .apply();
        } catch (RuntimeException ignored) {
        }
    }

    /** 테마를 고르는 창: 동그란 미리보기를 누르면 바로 적용 */
    private void showThemePicker() {

        // [sh] 블랙홀 잠금: 보기만 허용, 테마 변경 막음
        if (BlackholeLock.blockIfLocked(this)) return;

        if (currentPlanetId == null || planetView == null) {
            return;
        }

        final String planetId = currentPlanetId;
        final int current = loadTheme(planetId);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(8), dp(14), dp(4));

        final AlertDialog[] holder = new AlertDialog[1];

        final int columns = 3;

        LinearLayout row = null;

        for (int i = 0; i < PlanetWorldView.THEMES.length; i++) {

            if (i % columns == 0) {

                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);

                content.addView(
                        row,
                        new LinearLayout.LayoutParams(-1, -2)
                );
            }

            final int index = i;
            PlanetWorldView.Theme t = PlanetWorldView.THEMES[i];

            LinearLayout tile = new LinearLayout(this);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setGravity(Gravity.CENTER_HORIZONTAL);
            tile.setPadding(dp(4), dp(10), dp(4), dp(10));

            GradientDrawable ball = new GradientDrawable();
            ball.setShape(GradientDrawable.OVAL);
            ball.setGradientType(GradientDrawable.RADIAL_GRADIENT);
            ball.setGradientRadius(dp(44));
            ball.setGradientCenter(0.35f, 0.35f);
            ball.setColors(t.planet);

            if (i == current) {
                ball.setStroke(dp(3), C_ACCENT);
            }

            View preview = new View(this);
            preview.setBackground(ball);

            tile.addView(
                    preview,
                    new LinearLayout.LayoutParams(dp(58), dp(58))
            );

            TextView name = makeText(
                    t.name, 13,
                    i == current ? C_ACCENT : C_TEXT,
                    i == current
            );

            name.setGravity(Gravity.CENTER);
            name.setPadding(0, dp(6), 0, 0);

            tile.addView(name);

            tile.setOnClickListener(v -> {

                saveTheme(planetId, index);
                currentThemeIndex = index;
                planetView.setTheme(index);
                if (planetRef != null && !browseMode) {
                    planetRef.update("themeIndex", index)
                            .addOnFailureListener(this, e -> message("테마 저장 실패: " + e.getMessage()));
                }

                if (holder[0] != null) {
                    holder[0].dismiss();
                }
            });

            row.addView(
                    tile,
                    new LinearLayout.LayoutParams(0, -2, 1f)
            );
        }

        // 마지막 줄이 3칸이 안 차도 칸 너비가 같도록 빈 칸 채우기
        int rest = PlanetWorldView.THEMES.length % columns;

        if (rest != 0 && row != null) {

            for (int k = rest; k < columns; k++) {
                row.addView(
                        new View(this),
                        new LinearLayout.LayoutParams(0, 1, 1f)
                );
            }
        }

        holder[0] = new AlertDialog.Builder(
                this, R.style.StarRing_LightDialog)
                .setTitle("행성 테마")
                .setView(content)
                .setNegativeButton("닫기", null)
                .create();

        holder[0].show();
    }

    // ==========================================
    // Firestore 트랜잭션 수정
    // ==========================================

    private void savePhotoChanges(
            PhotoItem target,
            String newCaption,
            String newDate,
            AlertDialog dialog) {

        if (planetRef == null) {
            saving = false;
            message("행성 정보가 없습니다.");
            return;
        }

        db.<Void>runTransaction(transaction -> {

            // 최신 사진 목록을 먼저 읽기
            DocumentSnapshot snapshot =
                    transaction.get(planetRef);

            if (!snapshot.exists()) {
                throw new IllegalStateException(
                        "행성을 찾을 수 없습니다."
                );
            }

            Object rawPhotos = snapshot.get("photos");

            if (!(rawPhotos instanceof List)) {
                throw new IllegalStateException(
                        "사진 목록이 올바르지 않습니다."
                );
            }

            ArrayList<Map<String, Object>> updated =
                    new ArrayList<>();

            boolean found = false;

            for (Object element : (List<?>) rawPhotos) {

                if (!(element instanceof Map)) {
                    throw new IllegalStateException(
                            "사진 데이터가 올바르지 않습니다."
                    );
                }

                Map<?, ?> original = (Map<?, ?>) element;

                Map<String, Object> copy =
                        new HashMap<>();

                for (Map.Entry<?, ?> entry :
                        original.entrySet()) {

                    if (entry.getKey() instanceof String) {
                        copy.put(
                                (String) entry.getKey(),
                                entry.getValue()
                        );
                    }
                }

                String publicId =
                        readString(copy, "publicId");

                String imageUrl =
                        readString(copy, "imageUrl");

                // Cloudinary 이미지 식별자로
                // 현재 수정할 사진만 찾아 변경
                if (target.publicId.equals(publicId)
                        && target.url.equals(imageUrl)) {

                    copy.put("caption", newCaption);
                    copy.put("date", newDate);

                    found = true;
                }

                updated.add(copy);
            }

            if (!found) {
                throw new IllegalStateException(
                        "수정할 사진을 찾을 수 없습니다."
                );
            }

            // 다른 행성 필드는 그대로 유지
            transaction.update(
                    planetRef,
                    "photos",
                    updated,
                    "updatedAt",
                    FieldValue.serverTimestamp()
            );

            return null;

        }).addOnSuccessListener(this, unused -> {

            saving = false;
            dialog.dismiss();

            // 성공 메시지 중복 없이
            // Firestore 리스너가 변경된 내용 표시
            renderSelectedPhoto();

        }).addOnFailureListener(this, error -> {

            saving = false;

            dialog.getButton(
                    AlertDialog.BUTTON_POSITIVE
            ).setEnabled(true);

            message(
                    "사진 정보 수정 실패: "
                            + error.getMessage()
            );
        });
    }

    // ==========================================
    // 사진 그리드
    // ==========================================

    /** 그리드의 한 줄: 날짜 구분선 또는 사진 한 장 */
    private static class Row {

        final boolean header;
        final int photoIndex;   // 사진일 때 photos 목록의 번호
        final String label;     // 헤더일 때 "24일"

        Row(boolean header, int photoIndex, String label) {
            this.header = header;
            this.photoIndex = photoIndex;
            this.label = label;
        }
    }

    // 날짜 그룹 순서: true = 최근 날짜가 위, false = 오래된 날짜가 위
    private static final boolean NEWEST_FIRST = true;

    private class PhotoAdapter
            extends RecyclerView.Adapter<PhotoHolder> {

        private static final int TYPE_PHOTO = 0;
        private static final int TYPE_HEADER = 1;

        private final ArrayList<Row> rows = new ArrayList<>();

        /** 사진 목록을 날짜별로 묶어 구분선과 함께 다시 만듦 */
        void reload() {

            rows.clear();

            ArrayList<Integer> order = new ArrayList<>();

            for (int i = 0; i < photos.size(); i++) {
                order.add(i);
            }

            // 날짜순 정렬 (같은 날짜는 올린 순서 유지)
            Collections.sort(order, (x, y) -> {

                String dx = validDate(photos.get(x).date)
                        ? photos.get(x).date : "";

                String dy = validDate(photos.get(y).date)
                        ? photos.get(y).date : "";

                // 날짜 없는 사진은 항상 맨 아래
                if (dx.isEmpty() != dy.isEmpty()) {
                    return dx.isEmpty() ? 1 : -1;
                }

                int c = dx.compareTo(dy);

                return NEWEST_FIRST ? -c : c;
            });

            String lastKey = null;

            for (int index : order) {

                String date = photos.get(index).date;

                String key = validDate(date) ? date : "";

                if (lastKey == null || !lastKey.equals(key)) {

                    rows.add(new Row(true, -1, headerLabel(key)));
                    lastKey = key;
                }

                rows.add(new Row(false, index, ""));
            }

            notifyDataSetChanged();
        }

        boolean isHeader(int position) {

            return position >= 0
                    && position < rows.size()
                    && rows.get(position).header;
        }

        /** photos 의 번호 -> 그리드에서의 위치 */
        int rowOfPhoto(int photoIndex) {

            for (int i = 0; i < rows.size(); i++) {

                Row row = rows.get(i);

                if (!row.header && row.photoIndex == photoIndex) {
                    return i;
                }
            }

            return RecyclerView.NO_POSITION;
        }

        private String headerLabel(String date) {

            if (date.isEmpty()) {
                return "날짜 없음";
            }

            int month = Integer.parseInt(date.substring(5, 7));
            int day = Integer.parseInt(date.substring(8, 10));

            // 이 행성의 달이면 "24일", 다른 달이면 "9월 24일"
            if (date.startsWith(planetMonthKey)) {
                return day + "일";
            }

            return month + "월 " + day + "일";
        }

        @Override
        public int getItemViewType(int position) {
            return rows.get(position).header
                    ? TYPE_HEADER
                    : TYPE_PHOTO;
        }

        @Override
        public PhotoHolder onCreateViewHolder(
                ViewGroup parent,
                int viewType) {

            if (viewType == TYPE_HEADER) {
                return createHeaderHolder();
            }

            FrameLayout tile =
                    new FrameLayout(
                            PlanetDetailActivity.this
                    );

            RecyclerView.LayoutParams params =
                    new RecyclerView.LayoutParams(
                            -1,
                            dp(112)
                    );

            params.setMargins(
                    dp(4), dp(4),
                    dp(4), dp(4)
            );

            tile.setLayoutParams(params);

            ImageView image =
                    new ImageView(
                            PlanetDetailActivity.this
                    );

            image.setScaleType(
                    ImageView.ScaleType.CENTER_CROP
            );

            FrameLayout.LayoutParams imageParams =
                    new FrameLayout.LayoutParams(
                            -1, -1
                    );

            imageParams.setMargins(
                    dp(3), dp(3),
                    dp(3), dp(3)
            );

            tile.addView(image, imageParams);

            return new PhotoHolder(tile, image, null);
        }

        /** "24일 ────────" 모양의 날짜 구분선 */
        private PhotoHolder createHeaderHolder() {

            LinearLayout bar =
                    new LinearLayout(PlanetDetailActivity.this);

            bar.setOrientation(LinearLayout.HORIZONTAL);
            bar.setGravity(Gravity.CENTER_VERTICAL);

            RecyclerView.LayoutParams params =
                    new RecyclerView.LayoutParams(-1, -2);

            params.setMargins(dp(6), dp(14), dp(6), dp(6));

            bar.setLayoutParams(params);

            TextView label =
                    makeText("", 14, C_ACCENT, true);

            bar.addView(
                    label,
                    new LinearLayout.LayoutParams(-2, -2)
            );

            View line = new View(PlanetDetailActivity.this);
            line.setBackgroundColor(C_LINE);

            LinearLayout.LayoutParams lineParams =
                    new LinearLayout.LayoutParams(0, dp(1), 1f);

            lineParams.leftMargin = dp(10);

            bar.addView(line, lineParams);

            return new PhotoHolder(bar, null, label);
        }

        @Override
        public void onBindViewHolder(
                PhotoHolder holder,
                int position) {

            Row row = rows.get(position);

            if (row.header) {
                holder.label.setText(row.label);
                return;
            }

            PhotoItem item = photos.get(row.photoIndex);

            boolean selected =
                    row.photoIndex == selectedIndex;

            holder.itemView.setBackground(
                    rounded(
                            C_ACCENT_SOFT,
                            selected ? C_ACCENT : 0,
                            3,
                            14
                    )
            );

            Glide.with(holder.image)
                    .load(item.url)
                    .centerCrop()
                    .into(holder.image);

            holder.itemView.setOnClickListener(v -> {

                int pos = holder.getBindingAdapterPosition();

                if (pos == RecyclerView.NO_POSITION
                        || pos >= rows.size()
                        || rows.get(pos).header) {
                    return;
                }

                selectPhoto(rows.get(pos).photoIndex);

                // 고른 사진이 위 미리보기에 보이도록 맨 위로
                galleryScroll.smoothScrollTo(0, 0);
            });
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }
    }

    private static class PhotoHolder
            extends RecyclerView.ViewHolder {

        final ImageView image;   // 사진 줄일 때
        final TextView label;    // 날짜 구분선일 때

        PhotoHolder(
                View itemView,
                ImageView image,
                TextView label) {

            super(itemView);
            this.image = image;
            this.label = label;
        }
    }

    // ==========================================
    // 공통 UI
    // ==========================================

    private TextView makeText(
            String value,
            int size,
            int color,
            boolean bold) {

        TextView view = new TextView(this);

        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);

        if (bold) {
            view.setTypeface(null, Typeface.BOLD);
        }

        return view;
    }

    private GradientDrawable rounded(
            int background,
            int borderColor,
            int borderWidthDp,
            int radiusDp) {

        GradientDrawable drawable =
                new GradientDrawable();

        drawable.setColor(background);
        drawable.setCornerRadius(dp(radiusDp));

        if (borderColor != 0) {

            drawable.setStroke(
                    dp(borderWidthDp),
                    borderColor
            );
        }

        return drawable;
    }

    private int dp(float value) {

        return Math.round(
                value * getResources()
                        .getDisplayMetrics().density
        );
    }

    private void message(String value) {

        Toast.makeText(
                this,
                value,
                Toast.LENGTH_SHORT
        ).show();
    }

    // ==========================================
    // 좋아요 / 행성 공개
    // ==========================================

    private void stopLikesListener() {
        if (likesListener != null) {
            likesListener.remove();
            likesListener = null;
        }
        likeCount = 0;
        liked = false;
        refreshLikeButton();
    }

    private void startLikesListener() {
        if (planetRef == null || likesListener != null) return;
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        final String path = planetRef.getPath();
        likesListener = planetRef.collection("likes").addSnapshotListener((snap, error) -> {
            if (planetRef == null || !path.equals(planetRef.getPath())) return;
            if (error != null) {
                android.util.Log.e("StarRingLikes", "좋아요 조회 실패", error);
                return;
            }
            if (snap == null) return;
            likeCount = snap.size();
            liked = false;
            for (DocumentSnapshot doc : snap.getDocuments()) {
                if (user.getUid().equals(doc.getId())) { liked = true; break; }
            }
            refreshLikeButton();
        });
    }

    private void refreshLikeButton() {
        if (btnLike == null) return;
        btnLike.setText((liked ? "♥ " : "♡ ") + likeCount);
        btnLike.setEnabled(planetRef != null && !likeBusy);
        btnLike.setAlpha(likeBusy ? 0.5f : 1f);
    }

    private void toggleLike() {
        if (planetRef == null || likeBusy) return;
        // [sh] 블랙홀 잠금: 하트 막음
        if (BlackholeLock.blockIfLocked(this)) return;
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) { message("로그인이 필요합니다."); return; }
        likeBusy = true;
        refreshLikeButton();
        DocumentReference ref = planetRef.collection("likes").document(user.getUid());
        com.google.android.gms.tasks.Task<Void> task;
        if (liked) task = ref.delete();
        else task = ref.set(Collections.singletonMap("createdAt", FieldValue.serverTimestamp()));
        task.addOnSuccessListener(this, unused -> { likeBusy = false; refreshLikeButton(); })
                .addOnFailureListener(this, e -> {
                    likeBusy = false;
                    refreshLikeButton();
                    message("좋아요 처리 실패: " + e.getMessage());
                });
    }

    private void togglePublication() {
        if (browseMode || planetRef == null) return;
        // [sh] 블랙홀 잠금: 보기만 허용, 공개 전환 막음
        if (BlackholeLock.blockIfLocked(this)) return;
        if (publicPlanet) {
            updatePublication(false);
        } else {
            new AlertDialog.Builder(this, R.style.StarRing_LightDialog)
                    .setTitle("다른 은하계에 공개")
                    .setMessage("이 행성의 사진, 날짜, 설명이 다른 Star Ring 사용자에게 공개됩니다. 공개할까요?")
                    .setNegativeButton("취소", null)
                    .setPositiveButton("공개", (dialog, which) -> updatePublication(true))
                    .show();
        }
    }

    private void updatePublication(boolean publish) {
        if (planetRef == null || galaxyId == null) return;
        if (btnPublish != null) btnPublish.setEnabled(false);
        final DocumentReference targetRef = planetRef;
        db.collection("galaxies").document(galaxyId).get()
                .addOnSuccessListener(this, galaxyDoc -> {
                    String name = galaxyDoc.getString("name");
                    if (name == null || name.trim().isEmpty()) {
                        if (btnPublish != null) btnPublish.setEnabled(true);
                        message("은하계 이름을 찾을 수 없습니다.");
                        return;
                    }
                    Map<String, Object> changes = new HashMap<>();
                    changes.put("isPublic", publish);
                    changes.put("publicGalaxyName", name);
                    changes.put("themeIndex", currentThemeIndex);
                    targetRef.update(changes)
                            .addOnSuccessListener(this, unused -> {
                                if (btnPublish != null) btnPublish.setEnabled(true);
                                message(publish ? "다른 은하계에 공개했어요." : "행성을 비공개로 전환했어요.");
                            })
                            .addOnFailureListener(this, e -> {
                                if (btnPublish != null) btnPublish.setEnabled(true);
                                message("공개 설정 실패: " + e.getMessage());
                            });
                })
                .addOnFailureListener(this, e -> {
                    if (btnPublish != null) btnPublish.setEnabled(true);
                    message("은하계 조회 실패: " + e.getMessage());
                });
    }

    // ==========================================
    // 생명주기
    // ==========================================

    @Override
    protected void onStart() {
        super.onStart();
        startPlanetListener();
        startLikesListener();
        // 다른 화면에서 행성이 새로 생겼을 수 있으니 목록 갱신
        if (!browseMode) loadPlanetList();
    }

    @Override
    protected void onDestroy() {
        thumbExecutor.shutdownNow();
        super.onDestroy();
    }

    @Override
    protected void onStop() {

        stopLikesListener();
        if (planetListener != null) {
            planetListener.remove();
            planetListener = null;
        }

        super.onStop();
    }
}

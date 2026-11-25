
package com.example.starring;

import android.app.Activity;
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
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;


public class PlanetDetailActivity extends Activity {

    public static final String EXTRA_PLANET_ID = "planet_id";

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
    private LinearLayout planetPage;
    private TextView tabGallery;
    private TextView tabPlanet;

    private PlanetWorldView planetView;
    private TextView textYear;
    private ImageView infoThumb;
    private TextView infoTitle;
    private TextView infoSub;
    private TextView infoOpen;

    private final ArrayList<Integer> years = new ArrayList<>();
    private int planetYear = 0;
    private int selectedMonth = 0;
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

        createScreen();

        String planetId =
                getIntent().getStringExtra(EXTRA_PLANET_ID);

        if (planetId == null || planetId.isEmpty()) {
            finish();
            return;
        }

        loadPlanetReference(planetId);
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

        TextView title = makeText(
                "행성 상세", 20, C_TEXT, true
        );

        LinearLayout.LayoutParams titleParams =
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                );

        titleParams.leftMargin = dp(4);

        top.addView(title, titleParams);

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

        root.addView(
                galleryPage,
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

        btnAddPhotos.setOnClickListener(v -> {

            if (saving) {
                return;
            }

            String planetId = getIntent()
                    .getStringExtra(EXTRA_PLANET_ID);

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

        photoGrid.setLayoutManager(
                new GridLayoutManager(this, 3)
        );

        photoGrid.setPadding(
                dp(12), dp(4),
                dp(12), dp(12)
        );

        photoGrid.setClipToPadding(false);

        adapter = new PhotoAdapter();
        photoGrid.setAdapter(adapter);

        galleryPage.addView(
                photoGrid,
                new LinearLayout.LayoutParams(
                        -1, 0, 1f
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

        // 연도 이동:  ‹  2026년  ›
        LinearLayout yearBar = new LinearLayout(this);
        yearBar.setGravity(Gravity.CENTER);

        TextView yearPrev = makeText("‹", 26, C_ACCENT, true);
        TextView yearNext = makeText("›", 26, C_ACCENT, true);

        yearPrev.setGravity(Gravity.CENTER);
        yearNext.setGravity(Gravity.CENTER);

        textYear = makeText("", 16, C_TEXT, true);
        textYear.setGravity(Gravity.CENTER);

        yearBar.addView(
                yearPrev,
                new LinearLayout.LayoutParams(dp(52), dp(40))
        );

        yearBar.addView(
                textYear,
                new LinearLayout.LayoutParams(dp(96), dp(40))
        );

        yearBar.addView(
                yearNext,
                new LinearLayout.LayoutParams(dp(52), dp(40))
        );

        planetPage.addView(
                yearBar,
                new LinearLayout.LayoutParams(-1, -2)
        );

        yearPrev.setOnClickListener(v -> moveYear(-1));
        yearNext.setOnClickListener(v -> moveYear(1));

        planetView = new PlanetWorldView(this);

        planetPage.addView(
                planetView,
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
                "달마다 모은 추억이 핀으로 꽂혀 있어요.",
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

        galleryPage.setVisibility(planet ? View.GONE : View.VISIBLE);
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

    /** 사진 날짜(yyyy-MM-dd)에서 연도를 읽음. 형식이 다르면 0 */
    private int yearOf(String date) {

        if (date == null || date.length() < 7) return 0;

        try {
            return Integer.parseInt(date.substring(0, 4));
        } catch (Exception e) {
            return 0;
        }
    }

    private void refreshPlanetPage() {

        if (planetView == null) return;

        // 사진이 있는 연도 목록
        TreeSet<Integer> set = new TreeSet<>();

        for (PhotoItem item : photos) {

            int year = yearOf(item.date);

            if (year > 0) set.add(year);
        }

        if (set.isEmpty()) {
            set.add(Calendar.getInstance().get(Calendar.YEAR));
        }

        years.clear();
        years.addAll(set);

        if (!years.contains(planetYear)) {
            planetYear = years.get(years.size() - 1);
        }

        textYear.setText(planetYear + "년");

        // 그 해의 달마다 핀 하나 (대표 사진은 가장 이른 날짜)
        ArrayList<PlanetWorldView.Pin> pins = new ArrayList<>();
        PlanetWorldView.Pin keep = null;

        for (int month = 1; month <= 12; month++) {

            String prefix = String.format(
                    Locale.US, "%04d-%02d", planetYear, month
            );

            int count = 0;
            int first = -1;

            for (int i = 0; i < photos.size(); i++) {

                String date = photos.get(i).date;

                if (date == null || !date.startsWith(prefix)) continue;

                count++;

                if (first < 0
                        || date.compareTo(photos.get(first).date) < 0) {
                    first = i;
                }
            }

            if (count == 0) continue;

            PhotoItem rep = photos.get(first);

            PlanetWorldView.Pin pin = new PlanetWorldView.Pin(
                    month, count, first, rep.url
            );

            Bitmap cached = thumbCache.get(rep.url);

            if (cached != null) {
                planetView.setPinThumb(pin, cached);
            } else {
                loadThumb(pin);
            }

            if (month == selectedMonth) keep = pin;

            pins.add(pin);
        }

        planetView.setPins(pins);

        selectedPin = keep;

        if (keep == null) selectedMonth = 0;

        planetView.setSelectedMonth(selectedMonth);
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

    private void moveYear(int step) {

        int index = years.indexOf(planetYear) + step;

        if (index < 0 || index >= years.size()) return;

        planetYear = years.get(index);
        selectedMonth = 0;

        refreshPlanetPage();
    }

    private void onPinClicked(PlanetWorldView.Pin pin) {

        selectedPin = pin;
        selectedMonth = pin.month;

        planetView.setSelectedMonth(selectedMonth);
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
                            ? "사진을 추가하면 달마다 핀이 꽂혀요."
                            : "달마다 모은 추억이 핀으로 꽂혀 있어요."
            );

            return;
        }

        PhotoItem rep = photos.get(selectedPin.photoIndex);

        infoTitle.setText(
                planetYear + "년 " + selectedPin.month + "월 · 사진 "
                        + selectedPin.count + "장"
        );

        infoSub.setText(
                rep.caption.isEmpty()
                        ? "탭해서 이 달의 사진을 볼 수 있어요."
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
            photoGrid.scrollToPosition(photoIndex);
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

                    planetRef = db.collection("galaxies")
                            .document(galaxyId)
                            .collection("planets")
                            .document(planetId);

                    startPlanetListener();
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

        adapter.notifyDataSetChanged();
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
            adapter.notifyItemChanged(previous);
        }

        adapter.notifyItemChanged(index);

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

            picker.getDatePicker().setMaxDate(
                    System.currentTimeMillis()
            );

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

    private class PhotoAdapter
            extends RecyclerView.Adapter<PhotoHolder> {

        @Override
        public PhotoHolder onCreateViewHolder(
                ViewGroup parent,
                int viewType) {

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

            return new PhotoHolder(tile, image);
        }

        @Override
        public void onBindViewHolder(
                PhotoHolder holder,
                int position) {

            PhotoItem item = photos.get(position);

            boolean selected =
                    position == selectedIndex;

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

                int index =
                        holder.getBindingAdapterPosition();

                if (index != RecyclerView.NO_POSITION) {
                    selectPhoto(index);
                }
            });
        }

        @Override
        public int getItemCount() {
            return photos.size();
        }
    }

    private static class PhotoHolder
            extends RecyclerView.ViewHolder {

        final ImageView image;

        PhotoHolder(
                View itemView,
                ImageView image) {

            super(itemView);
            this.image = image;
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
    // 생명주기
    // ==========================================

    @Override
    protected void onStart() {
        super.onStart();
        startPlanetListener();
    }

    @Override
    protected void onDestroy() {
        thumbExecutor.shutdownNow();
        super.onDestroy();
    }

    @Override
    protected void onStop() {

        if (planetListener != null) {
            planetListener.remove();
            planetListener = null;
        }

        super.onStop();
    }
}

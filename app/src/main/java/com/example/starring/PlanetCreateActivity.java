package com.example.starring;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.TypedValue;
import android.widget.ImageButton;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Size;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ImageView;
import android.text.Editable;
import android.text.TextWatcher;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.Source;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Random;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PlanetCreateActivity extends AppCompatActivity {

    private static final int MAX_PHOTOS = 100;

    // 화면 색상 (연한 라벤더 계열)
    private static final int C_BG = 0xFFF8F5FF;
    private static final int C_BAR = 0xFFF1ECFC;
    private static final int C_CARD = 0xF7FFFFFF;
    private static final int C_LINE = 0xFFE7E0F8;
    private static final int C_TEXT = 0xFF3F3A5A;
    private static final int C_SUB = 0xFF8F89A8;
    private static final int C_HINT = 0xFFB5B0C9;
    private static final int C_ACCENT = 0xFF7F6BE0;
    private static final int C_ACCENT_SOFT = 0xFFEFEAFF;
    private static final int C_ACCENT_LINE = 0xFFD3C9F5;
    private static final int C_DANGER = 0xFFD9707F;

    private static final String[] PLANET_ASSETS = {
            "planet_01",
            "planet_02",
            "planet_03",
            "planet_04",
            "planet_05",
            "planet_06"
    };

    // Cloudinary 설정값으로 변경할 것
    private static final String CLOUD_NAME =
            "ldwhbckg";

    private static final String UPLOAD_PRESET =
            "starring_upload";

    private final FirebaseFirestore db =
            FirebaseFirestore.getInstance();

    private final ArrayList<PhotoDraft> photos =
            new ArrayList<>();

    private final ExecutorService thumbnailExecutor =
            Executors.newFixedThreadPool(2);

    private final ExecutorService uploadExecutor =
            Executors.newSingleThreadExecutor();

    private PhotoAdapter adapter;

    private RecyclerView photoGrid;
    private TextView countText;
    private TextView emptyText;
    private TextView btnAddPhotos;
    private TextView btnDate;
    private TextView btnRemove;
    private TextView btnComplete;
    private TextView progressText;

    private EditText editCaption;

    private int selectedIndex = -1;

    private boolean updatingCaption = false;
    private boolean saving = false;

    // Android 기본 사진 선택기
    private final ActivityResultLauncher<PickVisualMediaRequest>
            photoPicker = registerForActivityResult(
            new ActivityResultContracts.PickMultipleVisualMedia(
                    MAX_PHOTOS
            ),
            this::onPhotosPicked
    );

    private static class PhotoDraft {

        Uri uri;
        String caption;
        String date;

        PhotoDraft(Uri uri, String date) {
            this.uri = uri;
            this.date = date;
            this.caption = "";
        }
    }

    public static final String EXTRA_PLANET_ID =
            "target_planet_id";

    private String existingPlanetId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 기존 행성에 사진을 추가하는 경우에만 ID 전달됨
        existingPlanetId = getIntent()
                .getStringExtra(EXTRA_PLANET_ID);

        SpaceBackdrop.applyLightWindow(this);

        createScreen();
        refreshSelection();

        if (existingPlanetId != null
                && !existingPlanetId.isEmpty()) {
            btnComplete.setText("사진 추가 완료");
        }
    }

    // -----------------------------------------
    // 화면 구성
    // -----------------------------------------

    private void createScreen() {

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        // 상태바/내비게이션 바 영역을 피해서 배치 (우주 재판장과 동일)
        root.setFitsSystemWindows(true);

        // 상단: 뒤로가기 + 제목 (우주 재판장 상단바와 같은 크기/배치)
        LinearLayout topBar = new LinearLayout(this);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(8), 0, dp(16), 0);

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

        topBar.addView(back, new LinearLayout.LayoutParams(
                dp(48), dp(48)
        ));

        TextView screenTitle = text(
                existingPlanetId == null
                        ? "새 행성 만들기"
                        : "사진 추가",
                20, C_TEXT, true
        );

        LinearLayout.LayoutParams titleParams =
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                );

        titleParams.leftMargin = dp(4);

        topBar.addView(screenTitle, titleParams);

        back.setOnClickListener(v -> {
            if (!saving) finish();
        });

        LinearLayout.LayoutParams barParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(56)
                );

        root.addView(topBar, barParams);

        // 스크롤 가능한 본문
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(16), dp(18), dp(16));

        scroll.addView(content);

        root.addView(
                scroll,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0,
                        1f
                )
        );

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(
                dp(16), dp(18), dp(16), dp(20)
        );
        panel.setBackground(round(
                C_CARD, C_LINE, 1, 24
        ));

        content.addView(panel);

        // 사진 선택 제목 + 선택 개수
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = text(
                "사진 선택", 18, C_TEXT, true
        );

        countText = text(
                "0 / 100장", 14, C_ACCENT, true
        );

        countText.setGravity(Gravity.CENTER);
        countText.setPadding(dp(12), 0, dp(12), 0);
        countText.setBackground(round(
                C_ACCENT_SOFT, C_ACCENT_SOFT, 0, 14
        ));

        titleRow.addView(
                title,
                new LinearLayout.LayoutParams(0, dp(40), 1f)
        );

        titleRow.addView(
                countText,
                new LinearLayout.LayoutParams(-2, dp(28))
        );

        panel.addView(titleRow);

        // 갤러리 실행 버튼
        btnAddPhotos = text(
                "＋  갤러리에서 사진 추가",
                15,
                C_ACCENT,
                true
        );

        btnAddPhotos.setGravity(Gravity.CENTER);
        btnAddPhotos.setBackground(round(
                C_ACCENT_SOFT, C_ACCENT_LINE, 1, 28
        ));

        LinearLayout.LayoutParams addParams =
                new LinearLayout.LayoutParams(
                        -1, dp(52)
                );

        addParams.topMargin = dp(8);
        addParams.bottomMargin = dp(14);

        panel.addView(btnAddPhotos, addParams);

        btnAddPhotos.setOnClickListener(v -> openGallery());

        // 사진을 선택하지 않았을 때 표시 (점선 상자)
        emptyText = text(
                "✦\n갤러리에서 사진을 선택해주세요.",
                14,
                C_SUB,
                false
        );

        emptyText.setGravity(Gravity.CENTER);
        emptyText.setLineSpacing(dp(6), 1f);

        GradientDrawable emptyBg = new GradientDrawable();
        emptyBg.setColor(C_BG);
        emptyBg.setCornerRadius(dp(20));
        emptyBg.setStroke(dp(1), C_ACCENT_LINE, dp(5), dp(4));
        emptyText.setBackground(emptyBg);

        panel.addView(
                emptyText,
                new LinearLayout.LayoutParams(-1, dp(170))
        );

        // 선택한 사진 미리보기: 3열
        photoGrid = new RecyclerView(this);

        photoGrid.setLayoutManager(
                new GridLayoutManager(this, 3)
        );

        adapter = new PhotoAdapter();
        photoGrid.setAdapter(adapter);

        // 3줄 정도 보이고, 더 많으면 내부 스크롤
        panel.addView(
                photoGrid,
                new LinearLayout.LayoutParams(-1, dp(352))
        );

        TextView guide = text(
                "사진을 누르면 설명과 날짜를 따로 설정할 수 있어요.",
                12,
                C_SUB,
                false
        );

        guide.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams guideParams =
                new LinearLayout.LayoutParams(-1, -2);

        guideParams.topMargin = dp(12);
        guideParams.bottomMargin = dp(18);

        panel.addView(guide, guideParams);

        // 사진 설명
        TextView captionLabel = text(
                "사진 설명", 14, C_TEXT, true
        );

        panel.addView(captionLabel);

        editCaption = new EditText(this);
        editCaption.setTextSize(15);
        editCaption.setTextColor(C_TEXT);
        editCaption.setHintTextColor(C_HINT);
        editCaption.setHint("선택한 사진의 추억을 입력해주세요.");
        editCaption.setSingleLine(false);
        editCaption.setMinLines(2);
        editCaption.setMaxLines(4);
        editCaption.setPadding(
                dp(16), dp(12), dp(16), dp(12)
        );

        editCaption.setBackground(round(
                C_BG, C_LINE, 1, 18
        ));

        LinearLayout.LayoutParams captionParams =
                new LinearLayout.LayoutParams(
                        -1, -2
                );

        captionParams.topMargin = dp(8);
        captionParams.bottomMargin = dp(18);

        panel.addView(editCaption, captionParams);

        // 설명은 현재 선택한 사진에만 저장
        editCaption.addTextChangedListener(
                new TextWatcher() {

                    @Override
                    public void beforeTextChanged(
                            CharSequence s,
                            int start,
                            int count,
                            int after) {
                    }

                    @Override
                    public void onTextChanged(
                            CharSequence s,
                            int start,
                            int before,
                            int count) {

                        if (updatingCaption) return;

                        PhotoDraft item = selectedPhoto();

                        if (item != null) {
                            item.caption = s.toString();
                        }
                    }

                    @Override
                    public void afterTextChanged(Editable s) {
                    }
                }
        );

        // 사진 날짜
        TextView dateLabel = text(
                "사진 날짜", 14, C_TEXT, true
        );

        panel.addView(dateLabel);

        btnDate = text(
                "날짜 선택", 15, C_SUB, false
        );

        btnDate.setGravity(Gravity.CENTER_VERTICAL);
        btnDate.setPadding(
                dp(16), 0, dp(16), 0
        );

        btnDate.setBackground(round(
                C_BG, C_LINE, 1, 18
        ));

        LinearLayout.LayoutParams dateParams =
                new LinearLayout.LayoutParams(
                        -1, dp(54)
                );

        dateParams.topMargin = dp(8);
        panel.addView(btnDate, dateParams);

        btnDate.setOnClickListener(v -> showDatePicker());

        // 선택한 사진 제거
        btnRemove = text(
                "선택한 사진 삭제",
                13,
                C_DANGER,
                false
        );

        btnRemove.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);

        LinearLayout.LayoutParams removeParams =
                new LinearLayout.LayoutParams(
                        -1, dp(40)
                );

        removeParams.topMargin = dp(10);
        panel.addView(btnRemove, removeParams);

        btnRemove.setOnClickListener(v -> removeSelected());

        // 업로드 진행 상태
        progressText = text(
                "", 13, C_ACCENT, true
        );

        progressText.setGravity(Gravity.CENTER);
        content.addView(progressText);

        // 완료 버튼 (연보라 그라데이션 알약 모양)
        btnComplete = text(
                "완료",
                18,
                Color.WHITE,
                true
        );

        btnComplete.setGravity(Gravity.CENTER);

        GradientDrawable doneBg = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xFF9C8CF0, 0xFFB79BF2}
        );
        doneBg.setCornerRadius(dp(30));
        btnComplete.setBackground(doneBg);

        LinearLayout.LayoutParams doneParams =
                new LinearLayout.LayoutParams(
                        -1, dp(58)
                );

        doneParams.setMargins(
                dp(24), dp(10), dp(24), dp(16)
        );

        root.addView(btnComplete, doneParams);

        btnComplete.setOnClickListener(v -> savePlanet());

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

    // -----------------------------------------
    // 갤러리에서 사진 선택
    // -----------------------------------------

    private void openGallery() {

        if (saving) return;

        if (photos.size() >= MAX_PHOTOS) {
            message("사진은 최대 100장까지 선택할 수 있습니다.");
            return;
        }

        photoPicker.launch(
                new PickVisualMediaRequest.Builder()
                        .setMediaType(
                                ActivityResultContracts
                                        .PickVisualMedia.ImageOnly.INSTANCE
                        )
                        .build()
        );
    }

    private void onPhotosPicked(List<Uri> uris) {

        if (uris == null || uris.isEmpty()) {
            return;
        }

        Set<String> existing = new HashSet<>();

        for (PhotoDraft item : photos) {
            existing.add(item.uri.toString());
        }

        int added = 0;
        int skipped = 0;

        for (Uri uri : uris) {

            if (photos.size() >= MAX_PHOTOS) {
                skipped++;
                continue;
            }

            if (!existing.add(uri.toString())) {
                skipped++;
                continue;
            }

            // 업로드 전까지 사진 읽기 권한 유지 시도
            try {
                getContentResolver().takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                );
            } catch (Exception ignored) {
                // 선택기 종류에 따라 지원하지 않을 수 있음
            }

            photos.add(new PhotoDraft(uri, todayString()));
            added++;
        }

        adapter.notifyDataSetChanged();

        if (selectedIndex == -1 && !photos.isEmpty()) {
            selectedIndex = 0;
        }

        refreshSelection();

        if (skipped > 0) {
            message(
                    "중복 사진 또는 100장 제한으로 "
                            + skipped + "장을 제외했습니다."
            );
        } else if (added > 0) {
            message(added + "장을 추가했습니다.");
        }
    }

    private PhotoDraft selectedPhoto() {

        if (selectedIndex < 0
                || selectedIndex >= photos.size()) {
            return null;
        }

        return photos.get(selectedIndex);
    }

    private void selectPhoto(int index) {

        if (saving) return;

        int previous = selectedIndex;
        selectedIndex = index;

        if (previous >= 0) {
            adapter.notifyItemChanged(previous);
        }

        adapter.notifyItemChanged(index);

        refreshSelection();
    }

    private void refreshSelection() {

        countText.setText(
                photos.size() + " / " + MAX_PHOTOS + "장"
        );

        boolean hasPhotos = !photos.isEmpty();

        emptyText.setVisibility(
                hasPhotos ? View.GONE : View.VISIBLE
        );

        photoGrid.setVisibility(
                hasPhotos ? View.VISIBLE : View.GONE
        );

        PhotoDraft item = selectedPhoto();

        updatingCaption = true;

        editCaption.setText(
                item == null ? "" : item.caption
        );

        updatingCaption = false;

        editCaption.setEnabled(item != null && !saving);
        btnDate.setEnabled(item != null && !saving);
        btnRemove.setEnabled(item != null && !saving);

        btnDate.setText(
                item == null
                        ? "날짜 선택"
                        : item.date
        );

        btnComplete.setAlpha(
                hasPhotos ? 1f : 0.55f
        );

        btnAddPhotos.setText(
                "＋  갤러리에서 사진 추가"
        );
    }

    private void removeSelected() {

        if (saving || selectedPhoto() == null) {
            return;
        }

        photos.remove(selectedIndex);

        if (photos.isEmpty()) {
            selectedIndex = -1;
        } else {
            selectedIndex = Math.min(
                    selectedIndex, photos.size() - 1
            );
        }

        adapter.notifyDataSetChanged();
        refreshSelection();
    }

    // -----------------------------------------
    // 사진별 날짜 설정
    // -----------------------------------------

    private void showDatePicker() {

        PhotoDraft item = selectedPhoto();

        if (item == null || saving) return;

        String[] parts = item.date.split("-");

        int year = Integer.parseInt(parts[0]);
        int month = Integer.parseInt(parts[1]) - 1;
        int day = Integer.parseInt(parts[2]);

        DatePickerDialog dialog = new DatePickerDialog(
                this,
                R.style.StarRing_LightDialog,
                (view, y, m, d) -> {

                    item.date = String.format(
                            Locale.US,
                            "%04d-%02d-%02d",
                            y, m + 1, d
                    );

                    refreshSelection();
                },
                year,
                month,
                day
        );

        // 미래 날짜 선택 방지
        dialog.getDatePicker().setMaxDate(
                System.currentTimeMillis()
        );

        dialog.show();
    }

    private String todayString() {

        return new SimpleDateFormat(
                "yyyy-MM-dd",
                Locale.US
        ).format(new Date());
    }

    // -----------------------------------------
    // 완료: Cloudinary + Firestore 저장
    // -----------------------------------------

    private void savePlanet() {

        if (saving) return;

        if (photos.isEmpty()) {
            message("사진을 먼저 선택해주세요.");
            return;
        }

        if (photos.size() > MAX_PHOTOS) {
            message("최대 100장까지 업로드할 수 있습니다.");
            return;
        }

        if (CLOUD_NAME.startsWith("YOUR_")
                || UPLOAD_PRESET.startsWith("YOUR_")) {

            message("Cloudinary 설정값을 먼저 입력해주세요.");
            return;
        }

        FirebaseUser user =
                FirebaseAuth.getInstance().getCurrentUser();

        if (user == null) {
            message("로그인이 필요합니다.");
            return;
        }

        // 업로드 중 UI 변경 방지
        saving = true;
        setBusy(true);

        // 현재 화면의 데이터를 복사
        ArrayList<PhotoDraft> items = new ArrayList<>();

        for (PhotoDraft photo : photos) {

            PhotoDraft copy = new PhotoDraft(
                    photo.uri,
                    photo.date
            );

            copy.caption = photo.caption.trim();
            items.add(copy);
        }

        String uid = user.getUid();

        uploadExecutor.execute(() -> {

            try {

                // 사용자 은하계 확인
                DocumentSnapshot userDoc = Tasks.await(
                        db.collection("users")
                                .document(uid)
                                .get()
                );

                String galaxyId =
                        userDoc.getString("galaxyId");

                if (galaxyId == null) {
                    throw new Exception(
                            "연결된 은하계가 없습니다."
                    );
                }

                DocumentSnapshot galaxy = Tasks.await(
                        db.collection("galaxies")
                                .document(galaxyId)
                                .get()
                );

                if (!"active".equals(
                        galaxy.getString("status"))) {

                    throw new Exception(
                            "활성화된 은하계가 아닙니다."
                    );
                }


                Object members = galaxy.get("memberUids");

                if (!(members instanceof List)
                        || !((List<?>) members).contains(uid)) {

                    throw new Exception(
                            "은하계에 접근할 권한이 없습니다."
                    );
                }

// 기존 행성에 사진을 추가하는 경우를 위한 참조
                final DocumentReference existingPlanetRef;

                if (existingPlanetId != null
                        && !existingPlanetId.isEmpty()) {

                    existingPlanetRef = db.collection("galaxies")
                            .document(galaxyId)
                            .collection("planets")
                            .document(existingPlanetId);

                    // Cloudinary 업로드 전에 기존 행성 확인
                    DocumentSnapshot existing = Tasks.await(
                            existingPlanetRef.get(Source.SERVER)
                    );

                    if (!existing.exists()) {
                        throw new Exception(
                                "기존 행성을 찾을 수 없습니다."
                        );
                    }

                    Object rawPhotos = existing.get("photos");

                    if (!(rawPhotos instanceof List)) {
                        throw new Exception(
                                "기존 사진 목록이 올바르지 않습니다."
                        );
                    }

                    int existingCount =
                            ((List<?>) rawPhotos).size();

                    // 기존 사진 + 새 사진 최대 100장
                    if (existingCount + items.size() > MAX_PHOTOS) {
                        throw new Exception(
                                "사진은 최대 100장까지 추가할 수 있습니다. "
                                        + "현재 " + existingCount + "장입니다."
                        );
                    }

                } else {

                    // 새로운 행성 생성
                    existingPlanetRef = null;
                }


                // 선택한 사진을 하나씩 Cloudinary에 업로드
                ArrayList<Map<String, Object>> uploaded =
                        new ArrayList<>();

                for (int i = 0; i < items.size(); i++) {

                    int progress = i + 1;

                    runOnUiThread(() ->
                            progressText.setText(
                                    "사진 업로드 중 "
                                            + progress
                                            + " / "
                                            + items.size()
                            )
                    );

                    PhotoDraft photo = items.get(i);

                    JSONObject result =
                            uploadToCloudinary(photo.uri);

                    Map<String, Object> info =
                            new HashMap<>();

                    info.put(
                            "imageUrl",
                            result.getString("secure_url")
                    );

                    info.put(
                            "publicId",
                            result.getString("public_id")
                    );

                    info.put("caption", photo.caption);
                    info.put("date", photo.date);

                    uploaded.add(info);
                }

                runOnUiThread(() ->
                        progressText.setText(
                                "행성 정보 저장 중..."
                        )
                );


                if (existingPlanetRef == null) {

                    // ==================================
                    // 새로운 행성 생성
                    // ==================================

                    Map<String, Object> planet =
                            new HashMap<>();

                    planet.put("createdBy", uid);

                    // planet_01 ~ planet_06 랜덤 선택
                    int randomIndex = new java.util.Random()
                            .nextInt(PLANET_ASSETS.length);

                    planet.put(
                            "planetAsset",
                            PLANET_ASSETS[randomIndex]
                    );

                    planet.put("photoCount", uploaded.size());
                    planet.put("photos", uploaded);

                    planet.put(
                            "createdAt",
                            FieldValue.serverTimestamp()
                    );

                    Tasks.await(
                            db.collection("galaxies")
                                    .document(galaxyId)
                                    .collection("planets")
                                    .document()
                                    .set(planet)
                    );

                } else {

                    // ==================================
                    // 기존 행성에 사진 추가
                    // ==================================

                    Tasks.await(
                            db.runTransaction(transaction -> {

                                DocumentSnapshot existing =
                                        transaction.get(
                                                existingPlanetRef
                                        );

                                if (!existing.exists()) {
                                    throw new IllegalStateException(
                                            "행성을 찾을 수 없습니다."
                                    );
                                }

                                Object rawPhotos =
                                        existing.get("photos");

                                if (!(rawPhotos instanceof List)) {
                                    throw new IllegalStateException(
                                            "사진 목록이 올바르지 않습니다."
                                    );
                                }

                                // 기존 사진 목록 복사
                                ArrayList<Object> merged =
                                        new ArrayList<>(
                                                (List<?>) rawPhotos
                                        );

                                // 동시 추가 상황에서도 100장 확인
                                if (merged.size()
                                        + uploaded.size() > MAX_PHOTOS) {

                                    throw new IllegalStateException(
                                            "행성 사진은 최대 100장입니다."
                                    );
                                }

                                // 기존 사진 뒤에 새 사진 추가
                                merged.addAll(uploaded);

                                transaction.update(
                                        existingPlanetRef,
                                        "photos", merged,
                                        "photoCount", merged.size(),
                                        "updatedAt",
                                        FieldValue.serverTimestamp()
                                );

                                return null;
                            })
                    );
                }

                runOnUiThread(() -> {

                    saving = false;

                    message(
                            existingPlanetId == null
                                    ? "행성이 생성되었습니다."
                                    : "사진이 추가되었습니다."
                    );

                    setResult(RESULT_OK);
                    finish();
                });

            } catch (Exception e) {

                runOnUiThread(() -> {

                    saving = false;
                    setBusy(false);

                    message(
                            "업로드 실패: "
                                    + e.getMessage()
                    );
                });
            }
        });
    }

    private void setBusy(boolean busy) {

        btnAddPhotos.setEnabled(!busy);
        btnComplete.setEnabled(!busy);
        btnDate.setEnabled(!busy);
        btnRemove.setEnabled(!busy);
        editCaption.setEnabled(!busy);

        btnComplete.setText(
                busy
                        ? "업로드 중..."
                        : existingPlanetId == null
                          ? "완료"
                          : "사진 추가 완료"
        );

        if (!busy) {
            progressText.setText("");
            refreshSelection();
        }
    }

    // -----------------------------------------
    // Cloudinary unsigned 이미지 업로드
    // -----------------------------------------

    private JSONObject uploadToCloudinary(Uri uri)
            throws Exception {

        String endpoint =
                "https://api.cloudinary.com/v1_1/"
                        + CLOUD_NAME + "/image/upload";

        String boundary =
                "StarRingBoundary" + System.nanoTime();

        HttpURLConnection connection =
                (HttpURLConnection) new URL(endpoint)
                        .openConnection();

        connection.setRequestMethod("POST");
        connection.setDoOutput(true);

        connection.setConnectTimeout(20000);
        connection.setReadTimeout(120000);

        connection.setChunkedStreamingMode(8192);

        connection.setRequestProperty(
                "Content-Type",
                "multipart/form-data; boundary=" + boundary
        );

        try {

            try (DataOutputStream out =
                         new DataOutputStream(
                                 connection.getOutputStream())) {

                String presetPart =
                        "--" + boundary + "\r\n"
                                + "Content-Disposition: form-data; "
                                + "name=\"upload_preset\"\r\n\r\n"
                                + UPLOAD_PRESET + "\r\n";

                out.write(
                        presetPart.getBytes(
                                StandardCharsets.UTF_8
                        )
                );

                String mime =
                        getContentResolver().getType(uri);

                if (mime == null
                        || !mime.startsWith("image/")) {
                    mime = "image/jpeg";
                }

                String fileHeader =
                        "--" + boundary + "\r\n"
                                + "Content-Disposition: form-data; "
                                + "name=\"file\"; filename=\"photo\"\r\n"
                                + "Content-Type: " + mime + "\r\n\r\n";

                out.write(
                        fileHeader.getBytes(
                                StandardCharsets.UTF_8
                        )
                );

                try (InputStream input =
                             getContentResolver()
                                     .openInputStream(uri)) {

                    if (input == null) {
                        throw new Exception(
                                "사진을 읽을 수 없습니다."
                        );
                    }

                    byte[] buffer = new byte[8192];
                    int length;

                    while ((length = input.read(buffer)) != -1) {
                        out.write(buffer, 0, length);
                    }
                }

                String ending =
                        "\r\n--" + boundary + "--\r\n";

                out.write(
                        ending.getBytes(
                                StandardCharsets.UTF_8
                        )
                );
            }

            int status = connection.getResponseCode();

            InputStream responseStream =
                    status >= 200 && status < 300
                            ? connection.getInputStream()
                            : connection.getErrorStream();

            if (responseStream == null) {
                throw new Exception(
                        "Cloudinary 응답을 받지 못했습니다."
                );
            }

            ByteArrayOutputStream response =
                    new ByteArrayOutputStream();

            try (InputStream input = responseStream) {

                byte[] buffer = new byte[4096];
                int count;

                while ((count = input.read(buffer)) != -1) {
                    response.write(buffer, 0, count);
                }
            }

            String responseText = response.toString("UTF-8");

            if (status < 200 || status >= 300) {
                throw new Exception(
                        "Cloudinary HTTP "
                                + status + ": "
                                + responseText
                );
            }

            return new JSONObject(responseText);

        } finally {
            connection.disconnect();
        }
    }

    // -----------------------------------------
    // 사진 미리보기 그리드
    // -----------------------------------------

    private class PhotoAdapter
            extends RecyclerView.Adapter<PhotoHolder> {

        @Override
        public PhotoHolder onCreateViewHolder(
                ViewGroup parent,
                int viewType) {

            FrameLayout tile = new FrameLayout(
                    PlanetCreateActivity.this
            );

            tile.setLayoutParams(
                    new RecyclerView.LayoutParams(
                            -1, dp(112)
                    )
            );

            ImageView image = new ImageView(
                    PlanetCreateActivity.this
            );

            image.setScaleType(
                    ImageView.ScaleType.CENTER_CROP
            );

            FrameLayout.LayoutParams imageParams =
                    new FrameLayout.LayoutParams(
                            -1, -1
                    );

            imageParams.setMargins(
                    dp(5), dp(5), dp(5), dp(5)
            );

            tile.addView(image, imageParams);

            TextView check = text(
                    "✓", 15, Color.WHITE, true
            );

            check.setGravity(Gravity.CENTER);
            check.setBackground(round(
                    C_ACCENT, C_ACCENT, 0, 14
            ));

            FrameLayout.LayoutParams checkParams =
                    new FrameLayout.LayoutParams(
                            dp(27), dp(27),
                            Gravity.TOP | Gravity.RIGHT
                    );

            checkParams.setMargins(
                    0, dp(8), dp(8), 0
            );

            tile.addView(check, checkParams);

            return new PhotoHolder(tile, image, check);
        }

        @Override
        public void onBindViewHolder(
                PhotoHolder holder,
                int position) {

            PhotoDraft photo = photos.get(position);
            Uri uri = photo.uri;

            boolean active = position == selectedIndex;

            holder.itemView.setBackground(
                    round(
                            C_ACCENT_SOFT,
                            active
                                    ? C_ACCENT
                                    : C_ACCENT_SOFT,
                            active ? 3 : 0,
                            14
                    )
            );

            holder.check.setVisibility(
                    active ? View.VISIBLE : View.GONE
            );

            holder.image.setImageDrawable(null);
            holder.image.setBackgroundColor(C_ACCENT_SOFT);
            holder.image.setTag(uri);

            // 원본 이미지 전체를 로드하지 않고
            // 썸네일 크기로 축소하여 메모리 사용 감소
            thumbnailExecutor.execute(() -> {

                Bitmap bitmap = loadThumbnail(uri);

                holder.image.post(() -> {

                    Object tag = holder.image.getTag();

                    if (bitmap != null
                            && uri.equals(tag)) {

                        holder.image.setImageBitmap(bitmap);
                    }
                });
            });

            holder.itemView.setOnClickListener(
                    v -> selectPhoto(
                            holder.getBindingAdapterPosition()
                    )
            );
        }

        @Override
        public int getItemCount() {
            return photos.size();
        }
    }

    private static class PhotoHolder
            extends RecyclerView.ViewHolder {

        ImageView image;
        TextView check;

        PhotoHolder(
                View item,
                ImageView image,
                TextView check) {

            super(item);
            this.image = image;
            this.check = check;
        }
    }

    private Bitmap loadThumbnail(Uri uri) {

        try {

            if (Build.VERSION.SDK_INT >= 29) {

                return getContentResolver().loadThumbnail(
                        uri,
                        new Size(240, 240),
                        null
                );
            }

            BitmapFactory.Options options =
                    new BitmapFactory.Options();

            options.inJustDecodeBounds = true;

            try (InputStream input =
                         getContentResolver()
                                 .openInputStream(uri)) {

                BitmapFactory.decodeStream(
                        input, null, options
                );
            }

            int sample = 1;

            while (options.outWidth / sample > 300
                    || options.outHeight / sample > 300) {
                sample *= 2;
            }

            options.inJustDecodeBounds = false;
            options.inSampleSize = sample;

            try (InputStream input =
                         getContentResolver()
                                 .openInputStream(uri)) {

                return BitmapFactory.decodeStream(
                        input, null, options
                );
            }

        } catch (Exception e) {
            return null;
        }
    }

    // -----------------------------------------
    // 화면 디자인 공통 함수
    // -----------------------------------------

    private TextView text(
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

    private GradientDrawable round(
            int background,
            int border,
            int borderWidth) {

        return round(background, border, borderWidth, 16);
    }

    private GradientDrawable round(
            int background,
            int border,
            int borderWidth,
            int radiusDp) {

        GradientDrawable drawable =
                new GradientDrawable();

        drawable.setColor(background);
        drawable.setCornerRadius(dp(radiusDp));

        if (borderWidth > 0) {
            drawable.setStroke(
                    dp(borderWidth),
                    border
            );
        }

        return drawable;
    }

    private int dp(float size) {

        return Math.round(
                size * getResources()
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

    @Override
    public void onBackPressed() {
        if (!saving) {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        thumbnailExecutor.shutdownNow();
        uploadExecutor.shutdown();
        super.onDestroy();
    }
}

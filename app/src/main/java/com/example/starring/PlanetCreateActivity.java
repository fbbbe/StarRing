package com.example.starring;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        createScreen();
        refreshSelection();
    }

    // -----------------------------------------
    // 화면 구성
    // -----------------------------------------

    private void createScreen() {

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);

        // 상단: 뒤로가기 버튼만 배치
        LinearLayout topBar = new LinearLayout(this);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(20), 0, dp(20), 0);
        topBar.setBackgroundColor(0xFFF0EFF4);

        TextView back = text("‹", 42, 0xFF36343F, true);
        back.setGravity(Gravity.CENTER);
        back.setContentDescription("뒤로가기");

        topBar.addView(back, new LinearLayout.LayoutParams(
                dp(58), dp(64)
        ));

        back.setOnClickListener(v -> {
            if (!saving) finish();
        });

        root.addView(topBar);

        // 스크롤 가능한 본문
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(20), dp(20), dp(20));

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
                dp(16), dp(20), dp(16), dp(20)
        );
        panel.setBackground(round(
                0xFFF6F3FC, 0xFFE2DDEC, 1
        ));

        content.addView(panel);

        // 사진 선택 제목 + 선택 개수
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = text(
                "사진 선택", 20, 0xFF55515E, true
        );

        countText = text(
                "0 / 100장", 16, 0xFF6B4CD8, true
        );

        titleRow.addView(
                title,
                new LinearLayout.LayoutParams(0, dp(44), 1f)
        );

        countText.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        titleRow.addView(countText);

        panel.addView(titleRow);

        // 갤러리 실행 버튼
        btnAddPhotos = text(
                "＋  갤러리에서 사진 추가",
                16,
                0xFF694DD8,
                true
        );

        btnAddPhotos.setGravity(Gravity.CENTER);
        btnAddPhotos.setBackground(round(
                Color.WHITE, 0xFFCBC3EC, 1
        ));

        LinearLayout.LayoutParams addParams =
                new LinearLayout.LayoutParams(
                        -1, dp(54)
                );

        addParams.bottomMargin = dp(14);

        panel.addView(btnAddPhotos, addParams);

        btnAddPhotos.setOnClickListener(v -> openGallery());

        // 사진을 선택하지 않았을 때 표시
        emptyText = text(
                "갤러리에서 사진을 선택해주세요.",
                15,
                0xFF9993AB,
                false
        );

        emptyText.setGravity(Gravity.CENTER);

        panel.addView(
                emptyText,
                new LinearLayout.LayoutParams(-1, dp(180))
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
                0xFF8B849E,
                false
        );

        LinearLayout.LayoutParams guideParams =
                new LinearLayout.LayoutParams(-1, -2);

        guideParams.topMargin = dp(12);
        guideParams.bottomMargin = dp(18);

        panel.addView(guide, guideParams);

        // 사진 설명
        TextView captionLabel = text(
                "사진 설명", 15, 0xFF666071, true
        );

        panel.addView(captionLabel);

        editCaption = new EditText(this);
        editCaption.setTextSize(16);
        editCaption.setTextColor(0xFF393343);
        editCaption.setHintTextColor(0xFFABA8B2);
        editCaption.setHint("선택한 사진의 추억을 입력해주세요.");
        editCaption.setSingleLine(false);
        editCaption.setMinLines(2);
        editCaption.setMaxLines(4);
        editCaption.setPadding(
                dp(14), dp(12), dp(14), dp(12)
        );

        editCaption.setBackground(round(
                Color.WHITE, 0xFFCBC8D1, 1
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
                "사진 날짜", 15, 0xFF666071, true
        );

        panel.addView(dateLabel);

        btnDate = text(
                "날짜 선택", 16, 0xFF77717E, false
        );

        btnDate.setGravity(Gravity.CENTER_VERTICAL);
        btnDate.setPadding(
                dp(16), 0, dp(16), 0
        );

        btnDate.setBackground(round(
                Color.WHITE, 0xFFCBC8D1, 1
        ));

        LinearLayout.LayoutParams dateParams =
                new LinearLayout.LayoutParams(
                        -1, dp(58)
                );

        dateParams.topMargin = dp(8);
        panel.addView(btnDate, dateParams);

        btnDate.setOnClickListener(v -> showDatePicker());

        // 선택한 사진 제거
        btnRemove = text(
                "선택한 사진 삭제",
                14,
                0xFFC65B65,
                false
        );

        btnRemove.setGravity(Gravity.END);

        LinearLayout.LayoutParams removeParams =
                new LinearLayout.LayoutParams(
                        -1, dp(40)
                );

        removeParams.topMargin = dp(14);
        panel.addView(btnRemove, removeParams);

        btnRemove.setOnClickListener(v -> removeSelected());

        // 업로드 진행 상태
        progressText = text(
                "", 13, 0xFF6B4CD8, true
        );

        progressText.setGravity(Gravity.CENTER);
        content.addView(progressText);

        // 완료 버튼
        btnComplete = text(
                "완료",
                21,
                Color.WHITE,
                true
        );

        btnComplete.setGravity(Gravity.CENTER);
        btnComplete.setBackground(round(
                0xFF6B4CD8, 0xFF6B4CD8, 0
        ));

        LinearLayout.LayoutParams doneParams =
                new LinearLayout.LayoutParams(
                        -1, dp(65)
                );

        doneParams.setMargins(
                dp(28), dp(12), dp(28), dp(10)
        );

        root.addView(btnComplete, doneParams);

        btnComplete.setOnClickListener(v -> savePlanet());

        // 하단 태양계 메뉴 아이콘
        TextView sun = text(
                "☀",
                38,
                0xFFF1B749,
                true
        );

        sun.setGravity(Gravity.CENTER);
        sun.setBackgroundColor(0xFFF0EFF4);

        root.addView(
                sun,
                new LinearLayout.LayoutParams(-1, dp(66))
        );

        sun.setOnClickListener(v -> {
            if (!saving) finish();
        });

        setContentView(root);
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
                        : "▦   " + item.date
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

                Object members =
                        galaxy.get("memberUids");

                if (!(members instanceof List)
                        || !((List<?>) members).contains(uid)) {

                    throw new Exception(
                            "은하계에 접근할 권한이 없습니다."
                    );
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

                // 한 개의 행성 문서에 최대 100장 저장
                Map<String, Object> planet =
                        new HashMap<>();

                planet.put("createdBy", uid);
                planet.put("planetAsset", "planet_01");
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

                runOnUiThread(() -> {

                    saving = false;

                    message("행성이 생성되었습니다.");

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
                busy ? "업로드 중..." : "완료"
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
                    0xFF6B4CD8, 0xFF6B4CD8, 0
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
                            0xFFE6E0F6,
                            active
                                    ? 0xFF6B4CD8
                                    : 0xFFE6E0F6,
                            active ? 3 : 0
                    )
            );

            holder.check.setVisibility(
                    active ? View.VISIBLE : View.GONE
            );

            holder.image.setImageDrawable(null);
            holder.image.setBackgroundColor(0xFFE6E0F6);
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

        GradientDrawable drawable =
                new GradientDrawable();

        drawable.setColor(background);
        drawable.setCornerRadius(dp(16));

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

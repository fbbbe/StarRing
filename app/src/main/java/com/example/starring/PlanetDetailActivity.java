
package com.example.starring;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.graphics.Color;
import android.graphics.Typeface;
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
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;


public class PlanetDetailActivity extends Activity {

    public static final String EXTRA_PLANET_ID = "planet_id";

    private static final int MAX_CAPTION = 300;

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
        root.setBackgroundColor(0xFFF6F3FC);

        // 상단
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(16), 0, dp(16), 0);
        top.setBackgroundColor(0xFFF0EFF4);

        TextView back = makeText(
                "‹", 40, 0xFF36343F, true
        );

        back.setGravity(Gravity.CENTER);
        back.setContentDescription("뒤로가기");

        top.addView(
                back,
                new LinearLayout.LayoutParams(
                        dp(52), dp(65)
                )
        );

        back.setOnClickListener(v -> finish());

        TextView title = makeText(
                "행성 상세", 22, 0xFF383242, true
        );

        top.addView(title);
        root.addView(top);

        // 사진 확대 미리보기
        previewImage = new ImageView(this);

        previewImage.setScaleType(
                ImageView.ScaleType.FIT_CENTER
        );

        previewImage.setBackground(
                rounded(Color.WHITE, 0)
        );

        LinearLayout.LayoutParams previewParams =
                new LinearLayout.LayoutParams(
                        -1, dp(245)
                );

        previewParams.setMargins(
                dp(18), dp(18),
                dp(18), dp(12)
        );

        root.addView(previewImage, previewParams);

        // 설명
        textCaption = makeText(
                "사진을 불러오는 중...",
                16,
                0xFF494252,
                false
        );

        textCaption.setPadding(
                dp(22), dp(8), dp(22), dp(8)
        );

        root.addView(textCaption);

        // 날짜
        textDate = makeText(
                "", 14, 0xFF777080, false
        );

        textDate.setPadding(
                dp(22), dp(3), dp(22), dp(10)
        );

        root.addView(textDate);

        // 사진 정보 수정 버튼
        btnEdit = makeText(
                "사진 설명 · 날짜 수정",
                15,
                Color.WHITE,
                true
        );

        btnEdit.setGravity(Gravity.CENTER);

        btnEdit.setBackground(
                rounded(0xFF6B4CD8, 0)
        );

        LinearLayout.LayoutParams editParams =
                new LinearLayout.LayoutParams(
                        -1, dp(50)
                );

        editParams.setMargins(
                dp(22), dp(4),
                dp(22), dp(12)
        );

        root.addView(btnEdit, editParams);

        // 기존 행성에 사진 추가
        TextView btnAddPhotos = makeText(
                "＋ 사진 추가",
                15,
                0xFF6B4CD8,
                true
        );

        btnAddPhotos.setGravity(Gravity.CENTER);

        btnAddPhotos.setBackground(
                rounded(0xFFE9E2FA, 0)
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

        root.addView(btnAddPhotos, addParams);

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
                16,
                0xFF6B4CD8,
                true
        );

        textCount.setPadding(
                dp(22), dp(10),
                dp(22), dp(10)
        );

        root.addView(textCount);

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

        root.addView(
                photoGrid,
                new LinearLayout.LayoutParams(
                        -1, 0, 1f
                )
        );

        setContentView(root);
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
                item.caption.isEmpty()
                        ? "사진 설명 없음"
                        : item.caption
        );

        textDate.setText(
                item.date.isEmpty()
                        ? "날짜 정보 없음"
                        : "📅 " + item.date
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
                0xFF494252,
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
                0xFF494252,
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
                "📅 " + item.date,
                16,
                0xFF6B4CD8,
                true
        );

        dateButton.setPadding(
                dp(12), dp(16),
                dp(12), dp(16)
        );

        dateButton.setBackground(
                rounded(0xFFF6F3FC, 0)
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
                                        "📅 " + selectedDate[0]
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
                new AlertDialog.Builder(this)
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
                            0xFFE6E0F6,
                            selected ? 0xFF6B4CD8 : 0
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
            int borderColor) {

        GradientDrawable drawable =
                new GradientDrawable();

        drawable.setColor(background);
        drawable.setCornerRadius(dp(14));

        if (borderColor != 0) {

            drawable.setStroke(
                    dp(3),
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
    protected void onStop() {

        if (planetListener != null) {
            planetListener.remove();
            planetListener = null;
        }

        super.onStop();
    }
}

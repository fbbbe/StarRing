package com.example.starring;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PlanetDetailActivity extends Activity {

    public static final String EXTRA_PLANET_ID = "planet_id";

    private final FirebaseFirestore db =
            FirebaseFirestore.getInstance();

    private final ArrayList<PhotoItem> photos =
            new ArrayList<>();

    private ImageView previewImage;
    private TextView textCaption;
    private TextView textDate;
    private TextView textCount;

    private RecyclerView photoGrid;
    private PhotoAdapter adapter;

    private int selectedIndex = -1;

    private static class PhotoItem {

        final String url;
        final String caption;
        final String date;

        PhotoItem(String url, String caption, String date) {
            this.url = url;
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

        loadPlanet(planetId);
    }

    // -----------------------------------------
    // 화면 구성
    // -----------------------------------------

    private void createScreen() {

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFF6F3FC);

        // 상단 뒤로가기 및 제목
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
                "행성 상세",
                22,
                0xFF383242,
                true
        );

        top.addView(title);

        root.addView(top);

        // 선택한 사진 크게 표시
        previewImage = new ImageView(this);

        previewImage.setScaleType(
                ImageView.ScaleType.FIT_CENTER
        );

        previewImage.setBackground(
                rounded(Color.WHITE, 0)
        );

        LinearLayout.LayoutParams previewParams =
                new LinearLayout.LayoutParams(
                        -1, dp(250)
                );

        previewParams.setMargins(
                dp(18), dp(18),
                dp(18), dp(12)
        );

        root.addView(previewImage, previewParams);

        // 사진 설명
        textCaption = makeText(
                "사진을 불러오는 중...",
                16,
                0xFF494252,
                false
        );

        textCaption.setPadding(
                dp(22), dp(8),
                dp(22), dp(8)
        );

        root.addView(textCaption);

        // 사진 날짜
        textDate = makeText(
                "",
                14,
                0xFF777080,
                false
        );

        textDate.setPadding(
                dp(22), dp(2),
                dp(22), dp(15)
        );

        root.addView(textDate);

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

        // 사진 목록 3열
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

    // -----------------------------------------
    // Firestore에서 행성 데이터 조회
    // -----------------------------------------

    private void loadPlanet(String planetId) {

        FirebaseUser user =
                FirebaseAuth.getInstance().getCurrentUser();

        if (user == null) {
            message("로그인이 필요합니다.");
            finish();
            return;
        }

        // 현재 사용자의 은하계 확인
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

                    db.collection("galaxies")
                            .document(galaxyId)
                            .collection("planets")
                            .document(planetId)
                            .get()
                            .addOnSuccessListener(
                                    this,
                                    planetDoc -> {

                                        if (!planetDoc.exists()) {
                                            message("행성을 찾을 수 없습니다.");
                                            finish();
                                            return;
                                        }

                                        photos.clear();

                                        Object value = planetDoc.get("photos");

                                        if (value instanceof List) {

                                            for (Object element : (List<?>) value) {

                                                if (!(element instanceof Map)) {
                                                    continue;
                                                }

                                                Map<?, ?> data =
                                                        (Map<?, ?>) element;

                                                String url = readString(
                                                        data, "imageUrl"
                                                );

                                                if (url.isEmpty()) {
                                                    continue;
                                                }

                                                photos.add(new PhotoItem(
                                                        url,
                                                        readString(data, "caption"),
                                                        readString(data, "date")
                                                ));
                                            }
                                        }

                                        adapter.notifyDataSetChanged();
                                        textCount.setText(
                                                "추억 사진 " + photos.size() + "장"
                                        );

                                        if (photos.isEmpty()) {
                                            textCaption.setText(
                                                    "등록된 사진이 없습니다."
                                            );
                                            textDate.setText("");
                                        } else {
                                            selectPhoto(0);
                                        }
                                    })
                            .addOnFailureListener(this, error -> {

                                message(
                                        "사진 조회 실패: "
                                                + error.getMessage()
                                );
                            });
                })
                .addOnFailureListener(this, error -> {

                    message(
                            "은하계 확인 실패: "
                                    + error.getMessage()
                    );
                });
    }

    private String readString(
            Map<?, ?> data,
            String key) {

        Object value = data.get(key);

        return value instanceof String
                ? (String) value
                : "";
    }

    // -----------------------------------------
    // 사진 선택
    // -----------------------------------------

    private void selectPhoto(int index) {

        if (index < 0 || index >= photos.size()) {
            return;
        }

        int previous = selectedIndex;
        selectedIndex = index;

        PhotoItem item = photos.get(index);

        // 선택한 사진을 크게 표시
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
                        + (index + 1)
                        + "번째 사진"
        );

        if (previous >= 0) {
            adapter.notifyItemChanged(previous);
        }

        adapter.notifyItemChanged(index);
    }

    // -----------------------------------------
    // 사진 썸네일 목록
    // -----------------------------------------

    private class PhotoAdapter
            extends RecyclerView.Adapter<PhotoHolder> {

        @Override
        public PhotoHolder onCreateViewHolder(
                ViewGroup parent,
                int viewType) {

            FrameLayout tile = new FrameLayout(
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

            ImageView image = new ImageView(
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

            // 현재 선택된 사진은 보라색 테두리
            boolean selected = position == selectedIndex;

            holder.itemView.setBackground(
                    rounded(
                            0xFFE6E0F6,
                            selected ? 0xFF6B4CD8 : 0
                    )
            );

            Glide.with(PlanetDetailActivity.this)
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

        PhotoHolder(View itemView, ImageView image) {
            super(itemView);
            this.image = image;
        }
    }

    // -----------------------------------------
    // 공통 UI 함수
    // -----------------------------------------

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

    private void message(String text) {

        Toast.makeText(
                this,
                text,
                Toast.LENGTH_SHORT
        ).show();
    }
}

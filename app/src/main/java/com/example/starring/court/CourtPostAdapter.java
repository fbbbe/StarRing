package com.example.starring.court;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.starring.R;

import java.util.ArrayList;
import java.util.List;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;

/** 재판장 메인의 갈등 목록 카드 어댑터 */
public class CourtPostAdapter extends RecyclerView.Adapter<CourtPostAdapter.Holder> {

    public interface OnPostClickListener {
        void onPostClick(CourtPost post);
    }

    private static final String PAYLOAD_TICK = "tick";
    private static final int COLOR_URGENT = 0xFFFF6B6B;
    private static final int COLOR_EXPIRED = 0xFF8E8AA8;

    private final List<CourtPost> items = new ArrayList<>();
    private final OnPostClickListener listener;

    public CourtPostAdapter(OnPostClickListener listener) {
        this.listener = listener;
    }

    public void submit(List<CourtPost> posts) {
        items.clear();
        items.addAll(posts);
        notifyDataSetChanged();
    }

    /** 1초마다 호출: 남은 시간 글자만 다시 그림 (카드 전체를 다시 만들지 않음) */
    public void tick() {
        if (!items.isEmpty()) {
            notifyItemRangeChanged(0, items.size(), PAYLOAD_TICK);
        }
    }

    /**
     * 남은 시간 표시 공용 함수 (목록 / 투표 화면이 똑같이 사용)
     * - 평소: 기본 색
     * - 마감 1시간 미만: 붉은색 + 1초마다 깜빡
     * - 마감 후: 회색 "마감됨"
     */
    public static void bindRemaining(TextView view, CourtPost post, int normalColor) {
        view.setText(post.remainingText());
        if (post.isExpired()) {
            view.setTextColor(COLOR_EXPIRED);
            view.setAlpha(1f);
        } else if (post.isUrgent()) {
            view.setTextColor(COLOR_URGENT);
            long sec = System.currentTimeMillis() / 1000L;
            view.setAlpha(sec % 2 == 0 ? 1f : 0.45f);
        } else {
            view.setTextColor(normalColor);
            view.setAlpha(1f);
        }
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_court_post, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position, @NonNull List<Object> payloads) {
        if (!payloads.isEmpty()) {
            // 시간만 갱신
            bindRemaining(holder.remaining, items.get(position), holder.normalColor);
            return;
        }
        super.onBindViewHolder(holder, position, payloads);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        final CourtPost post = items.get(position);

        holder.title.setText("[" + post.title + "]");
        holder.bar.setProgress(post.percentA());
        holder.percentA.setText("A " + post.percentA() + "%");
        holder.percentB.setText("B " + post.percentB() + "%");
        bindRemaining(holder.remaining, post, holder.normalColor);
        applyDim(holder, post.isResolved());

        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) {
                    listener.onPostClick(post);
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    // 화해 완료된 글: 카드 위에 반투명 검정 (투표 후 A/B 카드와 같은 느낌)
    private void applyDim(Holder holder, boolean dim) {
        if (dim) {
            GradientDrawable shade = new GradientDrawable();
            shade.setColor(0x99000000);
            shade.setCornerRadius(16f * holder.itemView.getResources().getDisplayMetrics().density);
            holder.itemView.setForeground(shade);
        } else {
            holder.itemView.setForeground(holder.defaultForeground);
        }
    }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView title;
        final ProgressBar bar;
        final TextView percentA;
        final TextView percentB;
        final TextView remaining;
        final int normalColor;
        final Drawable defaultForeground;

        Holder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.textPostTitle);
            bar = itemView.findViewById(R.id.barVotes);
            percentA = itemView.findViewById(R.id.textPercentA);
            percentB = itemView.findViewById(R.id.textPercentB);
            remaining = itemView.findViewById(R.id.textRemaining);
            normalColor = remaining.getCurrentTextColor();
            defaultForeground = itemView.getForeground();
        }
    }
}

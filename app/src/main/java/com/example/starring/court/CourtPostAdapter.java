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

/** 재판장 메인의 갈등 목록 카드 어댑터 */
public class CourtPostAdapter extends RecyclerView.Adapter<CourtPostAdapter.Holder> {

    public interface OnPostClickListener {
        void onPostClick(CourtPost post);
    }

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

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_court_post, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        final CourtPost post = items.get(position);

        holder.title.setText("[" + post.title + "]");
        holder.bar.setProgress(post.percentA());
        holder.percentA.setText("A " + post.percentA() + "%");
        holder.percentB.setText("B " + post.percentB() + "%");
        holder.remaining.setText(post.remainingText());

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

    static class Holder extends RecyclerView.ViewHolder {
        final TextView title;
        final ProgressBar bar;
        final TextView percentA;
        final TextView percentB;
        final TextView remaining;

        Holder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.textPostTitle);
            bar = itemView.findViewById(R.id.barVotes);
            percentA = itemView.findViewById(R.id.textPercentA);
            percentB = itemView.findViewById(R.id.textPercentB);
            remaining = itemView.findViewById(R.id.textRemaining);
        }
    }
}

package com.example.starring.chat;

import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.starring.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** 내 말풍선은 오른쪽, 상대는 왼쪽. 날짜가 바뀌는 첫 메시지 위에 날짜 표시. */
final class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.Holder> {

    private final String myUid;
    private final List<ChatMessage> items = new ArrayList<>();
    // 표시용 (기기 시간대). 계산 기준값이 아님
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("a h:mm", Locale.KOREAN);
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy년 M월 d일 E요일", Locale.KOREAN);

    ChatAdapter(String myUid) {
        this.myUid = myUid;
    }

    void submit(List<ChatMessage> messages) {
        items.clear();
        items.addAll(messages);
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_chat_message, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        ChatMessage m = items.get(position);
        boolean mine = myUid.equals(m.senderUid);

        // 날짜 구분: 첫 메시지이거나 앞 메시지와 날짜가 다를 때
        Date prev = position > 0 ? items.get(position - 1).time : null;
        boolean showDate = m.time != null && (position == 0 || prev == null || !sameDay(prev, m.time));
        h.date.setVisibility(showDate ? View.VISIBLE : View.GONE);
        if (showDate) {
            h.date.setText(dateFormat.format(m.time));
        }

        h.bubble.setText(m.text);
        h.bubble.setBackgroundResource(mine ? R.drawable.bg_chat_mine : R.drawable.bg_chat_partner);
        h.bubble.setTextColor(ContextCompat.getColor(h.itemView.getContext(),
                mine ? R.color.on_nebula : R.color.text_primary));

        String time = m.pending
                ? h.itemView.getContext().getString(R.string.chat_sending)
                : (m.time == null ? "" : timeFormat.format(m.time));
        h.row.setGravity(Gravity.BOTTOM | (mine ? Gravity.END : Gravity.START));
        h.timeLeft.setVisibility(mine ? View.VISIBLE : View.GONE);
        h.timeRight.setVisibility(mine ? View.GONE : View.VISIBLE);
        (mine ? h.timeLeft : h.timeRight).setText(time);
        h.bubble.setAlpha(m.pending ? 0.7f : 1f);
    }

    private static boolean sameDay(Date a, Date b) {
        Calendar ca = Calendar.getInstance();
        Calendar cb = Calendar.getInstance();
        ca.setTime(a);
        cb.setTime(b);
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR)
                && ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR);
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView date;
        final LinearLayout row;
        final TextView timeLeft;
        final TextView bubble;
        final TextView timeRight;

        Holder(@NonNull View v) {
            super(v);
            date = v.findViewById(R.id.textDate);
            row = v.findViewById(R.id.row);
            timeLeft = v.findViewById(R.id.textTimeLeft);
            bubble = v.findViewById(R.id.textBubble);
            timeRight = v.findViewById(R.id.textTimeRight);
        }
    }
}

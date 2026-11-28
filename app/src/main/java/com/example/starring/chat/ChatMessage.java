package com.example.starring.chat;

import androidx.annotation.Nullable;

import com.example.starring.data.FirestoreSchema;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.Date;

/** 채팅 메시지 한 개 (galaxies/{galaxyId}/messages/{messageId}) */
final class ChatMessage {

    final String id;
    final String senderUid;
    final String text;
    /** 표시용 시각. 보낸 직후(serverTimestamp 미확정)에는 기기 시각 추정값, 그것도 없으면 null */
    @Nullable
    final Date time;
    /** 아직 서버에 저장되지 않음 (serverTimestamp null 상태) */
    final boolean pending;

    private ChatMessage(String id, String senderUid, String text, @Nullable Date time, boolean pending) {
        this.id = id;
        this.senderUid = senderUid;
        this.text = text;
        this.time = time;
        this.pending = pending;
    }

    static ChatMessage from(DocumentSnapshot doc) {
        // serverTimestamp는 쓰기 직후 로컬 스냅샷에서 null → 표시용으로만 추정값(ESTIMATE) 사용
        Timestamp confirmed = doc.getTimestamp(FirestoreSchema.MESSAGE_CREATED_AT);
        Timestamp estimate = doc.getTimestamp(FirestoreSchema.MESSAGE_CREATED_AT,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE);
        String text = doc.getString(FirestoreSchema.MESSAGE_TEXT);
        return new ChatMessage(
                doc.getId(),
                doc.getString(FirestoreSchema.MESSAGE_SENDER_UID),
                text == null ? "" : text,
                estimate == null ? null : estimate.toDate(),
                confirmed == null || doc.getMetadata().hasPendingWrites());
    }
}

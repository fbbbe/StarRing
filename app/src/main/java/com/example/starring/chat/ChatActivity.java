package com.example.starring.chat;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.starring.R;
import com.example.starring.common.InsetsHelper;
import com.example.starring.data.FirestoreSchema;
import com.example.starring.data.GalaxyRepository;
import com.example.starring.data.UserRepository;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.MetadataChanges;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 커플 채팅 (galaxies/{galaxyId}/messages).
 *
 * - 실시간: createdAt 오름차순 마지막 50개(limitToLast)를 listener로 받는다.
 * - 위로 스크롤하면 가장 오래된 메시지 이전 50개를 한 번 읽어(get) 앞에 붙인다.
 * - 새 메시지가 오면 listener 창(50개)이 밀려 맨 앞 메시지가 REMOVED로 빠지는데,
 *   메시지는 rules상 삭제가 불가능하므로 REMOVED = 창 밀림 → 버리지 않고 이전 목록(older)으로 옮긴다.
 * - 보낸 직후 createdAt(serverTimestamp)은 null → "전송 중"으로 표시, 정렬은 SDK 추정값 사용.
 * - 블랙홀 잠금 대상 아님 (화해를 위한 대화는 허용).
 */
public class ChatActivity extends AppCompatActivity {

    private static final int PAGE_SIZE = 50;

    private final UserRepository userRepository = new UserRepository();
    private final GalaxyRepository galaxyRepository = new GalaxyRepository();

    private String myUid;
    private CollectionReference messagesRef;
    private ListenerRegistration liveListener;

    /** 실시간 창보다 오래된 메시지 (오래된 순) */
    private final List<DocumentSnapshot> older = new ArrayList<>();
    /** 실시간 창 (오래된 순, 최대 50개) */
    private List<DocumentSnapshot> live = new ArrayList<>();
    private boolean loadingOlder = false;
    private boolean noMoreOlder = false;
    private boolean firstSnapshot = true;

    private RecyclerView list;
    private LinearLayoutManager layoutManager;
    private ChatAdapter adapter;
    private EditText editMessage;
    private MaterialButton btnSend;
    private TextView textCount;
    private TextView textEmpty;
    private TextView textTitle;
    private TextView textSubtitle;
    private ProgressBar progress;
    private ProgressBar progressOlder;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_chat);
        InsetsHelper.applySystemBarsAndIme(findViewById(R.id.content));

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            finish();
            return;
        }
        myUid = user.getUid();

        list = findViewById(R.id.listMessages);
        editMessage = findViewById(R.id.editMessage);
        btnSend = findViewById(R.id.btnSend);
        textCount = findViewById(R.id.textCount);
        textEmpty = findViewById(R.id.textEmpty);
        textTitle = findViewById(R.id.textTitle);
        textSubtitle = findViewById(R.id.textSubtitle);
        progress = findViewById(R.id.progress);
        progressOlder = findViewById(R.id.progressOlder);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        adapter = new ChatAdapter(myUid);
        layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        list.setLayoutManager(layoutManager);
        list.setAdapter(adapter);
        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView rv, int dx, int dy) {
                // 맨 위 근처까지 올리면 이전 메시지 불러오기
                if (dy < 0 && layoutManager.findFirstVisibleItemPosition() <= 2) {
                    loadOlder();
                }
            }
        });

        editMessage.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                updateInputState();
            }
        });
        btnSend.setOnClickListener(v -> send());

        loadGalaxy();
    }

    // ------------------------------------
    // 내 은하계 찾기 → 제목 + 실시간 구독
    // ------------------------------------

    private void loadGalaxy() {
        userRepository.getUser(myUid)
                .addOnSuccessListener(this, userDoc -> {
                    String galaxyId = userDoc.getString(FirestoreSchema.USER_GALAXY_ID);
                    if (galaxyId == null) {
                        fail(R.string.chat_no_galaxy);
                        return;
                    }
                    messagesRef = FirebaseFirestore.getInstance()
                            .collection(FirestoreSchema.GALAXIES)
                            .document(galaxyId)
                            .collection(FirestoreSchema.MESSAGES);
                    loadTitle(galaxyId);
                    startLive();
                })
                .addOnFailureListener(this, e -> fail(R.string.chat_load_failed));
    }

    private void loadTitle(String galaxyId) {
        galaxyRepository.getGalaxy(galaxyId).addOnSuccessListener(this, galaxy -> {
            String name = galaxy.getString(FirestoreSchema.GALAXY_NAME);
            if (name != null && !name.trim().isEmpty()) {
                textTitle.setText(getString(R.string.galaxy_name_format, name.trim()));
            }
            Object members = galaxy.get(FirestoreSchema.GALAXY_MEMBER_UIDS);
            if (!(members instanceof List)) {
                return;
            }
            for (Object uid : (List<?>) members) {
                if (uid instanceof String && !myUid.equals(uid)) {
                    userRepository.getUser((String) uid).addOnSuccessListener(this, partner -> {
                        String nickname = partner.getString(FirestoreSchema.USER_NICKNAME);
                        if (nickname != null) {
                            textSubtitle.setText(getString(R.string.chat_subtitle_format, nickname));
                            textSubtitle.setVisibility(View.VISIBLE);
                        }
                    });
                }
            }
        });
    }

    private void startLive() {
        Query query = messagesRef
                .orderBy(FirestoreSchema.MESSAGE_CREATED_AT, Query.Direction.ASCENDING)
                .limitToLast(PAGE_SIZE);
        // MetadataChanges.INCLUDE: "전송 중" → 서버 저장 완료로 바뀌는 순간도 받기 위해
        // Activity 범위 listener는 onStop에서 끊기고 다시 안 붙으므로 직접 붙이고 onDestroy에서 뗀다
        liveListener = query.addSnapshotListener(MetadataChanges.INCLUDE, (snap, e) -> {
            if (e != null) {
                // 연동이 해제된 은하계 등 → rules에서 거부
                fail(R.string.chat_load_failed);
                return;
            }
            if (snap == null) {
                return;
            }

            // 창이 밀려 빠진 메시지 → older 끝(가장 최근 쪽)에 보관
            List<DocumentChange> removed = new ArrayList<>();
            for (DocumentChange change : snap.getDocumentChanges()) {
                // 서버가 거부한 내 전송(아직 pending)은 보관하지 않음
                if (change.getType() == DocumentChange.Type.REMOVED
                        && !change.getDocument().getMetadata().hasPendingWrites()) {
                    removed.add(change);
                }
            }
            removed.sort((a, b) -> Integer.compare(a.getOldIndex(), b.getOldIndex()));
            for (DocumentChange change : removed) {
                older.add(change.getDocument());
            }

            boolean atBottom = !list.canScrollVertically(1);
            int before = adapter.getItemCount();
            live = new ArrayList<>(snap.getDocuments());
            if (firstSnapshot) {
                firstSnapshot = false;
                // 처음부터 50개가 안 되면 더 불러올 것도 없음
                noMoreOlder = live.size() < PAGE_SIZE;
            }
            render();
            progress.setVisibility(View.GONE);

            // 맨 아래를 보고 있었거나 내가 보낸 메시지면 아래로
            boolean myNew = false;
            for (DocumentChange change : snap.getDocumentChanges()) {
                if (change.getType() == DocumentChange.Type.ADDED
                        && myUid.equals(change.getDocument().getString(FirestoreSchema.MESSAGE_SENDER_UID))) {
                    myNew = true;
                }
            }
            if ((atBottom || myNew || before == 0) && adapter.getItemCount() > 0) {
                list.scrollToPosition(adapter.getItemCount() - 1);
            }
        });
    }

    // ------------------------------------
    // 이전 메시지 더 불러오기
    // ------------------------------------

    private void loadOlder() {
        if (loadingOlder || noMoreOlder || messagesRef == null) {
            return;
        }
        DocumentSnapshot oldest = !older.isEmpty() ? older.get(0) : (live.isEmpty() ? null : live.get(0));
        if (oldest == null || oldest.getTimestamp(FirestoreSchema.MESSAGE_CREATED_AT) == null) {
            return;
        }
        loadingOlder = true;
        progressOlder.setVisibility(View.VISIBLE);
        messagesRef.orderBy(FirestoreSchema.MESSAGE_CREATED_AT, Query.Direction.ASCENDING)
                .endBefore(oldest)
                .limitToLast(PAGE_SIZE)
                .get()
                .addOnSuccessListener(this, snap -> {
                    loadingOlder = false;
                    progressOlder.setVisibility(View.GONE);
                    List<DocumentSnapshot> page = snap.getDocuments();
                    if (page.size() < PAGE_SIZE) {
                        noMoreOlder = true;
                    }
                    if (page.isEmpty()) {
                        return;
                    }
                    // 보고 있던 메시지가 제자리에 남도록 위치 보정
                    int first = layoutManager.findFirstVisibleItemPosition();
                    View firstView = layoutManager.findViewByPosition(first);
                    int offset = firstView == null ? 0 : firstView.getTop() - list.getPaddingTop();
                    int beforeCount = adapter.getItemCount();
                    older.addAll(0, page);
                    render();
                    int added = adapter.getItemCount() - beforeCount;
                    layoutManager.scrollToPositionWithOffset(Math.max(0, first) + added, offset);
                })
                .addOnFailureListener(this, e -> {
                    loadingOlder = false;
                    progressOlder.setVisibility(View.GONE);
                });
    }

    private void render() {
        // older + live, 같은 id는 한 번만 (창 밀림 직후 겹칠 수 있음)
        Map<String, ChatMessage> merged = new LinkedHashMap<>();
        for (DocumentSnapshot doc : older) {
            merged.put(doc.getId(), ChatMessage.from(doc));
        }
        for (DocumentSnapshot doc : live) {
            merged.remove(doc.getId());
            merged.put(doc.getId(), ChatMessage.from(doc));
        }
        adapter.submit(new ArrayList<>(merged.values()));
        textEmpty.setVisibility(merged.isEmpty() ? View.VISIBLE : View.GONE);
    }

    // ------------------------------------
    // 보내기
    // ------------------------------------

    private void updateInputState() {
        int length = ChatPolicy.length(editMessage.getText().toString());
        btnSend.setEnabled(messagesRef != null && length >= 1 && length <= ChatPolicy.MAX_LENGTH);
        // 450자를 넘기면 글자 수 표시
        boolean showCount = length > ChatPolicy.MAX_LENGTH - 50;
        textCount.setVisibility(showCount ? View.VISIBLE : View.GONE);
        if (showCount) {
            textCount.setText(getString(R.string.chat_count_format, length, ChatPolicy.MAX_LENGTH));
            textCount.setTextColor(getColor(length > ChatPolicy.MAX_LENGTH
                    ? R.color.error_soft : R.color.text_secondary));
        }
    }

    private void send() {
        if (messagesRef == null) {
            return;
        }
        String raw = editMessage.getText().toString();
        String text = ChatPolicy.normalize(raw);
        if (text == null) {
            if (ChatPolicy.length(raw) > ChatPolicy.MAX_LENGTH) {
                Toast.makeText(this, getString(R.string.chat_too_long, ChatPolicy.MAX_LENGTH),
                        Toast.LENGTH_SHORT).show();
            }
            return;
        }

        Map<String, Object> message = new HashMap<>();
        message.put(FirestoreSchema.MESSAGE_SENDER_UID, myUid);
        message.put(FirestoreSchema.MESSAGE_TEXT, text);
        message.put(FirestoreSchema.MESSAGE_CREATED_AT, FieldValue.serverTimestamp());

        // 바로 비우고(로컬 스냅샷에 "전송 중"으로 먼저 보임), 실패하면 입력칸에 되돌림
        editMessage.setText("");
        messagesRef.add(message).addOnFailureListener(this, e -> {
            Toast.makeText(this, R.string.chat_send_failed, Toast.LENGTH_SHORT).show();
            if (editMessage.getText().length() == 0) {
                editMessage.setText(text);
                editMessage.setSelection(editMessage.getText().length());
            }
        });
    }

    private void fail(int messageRes) {
        if (isFinishing()) {
            return;
        }
        Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show();
        finish();
    }

    @Override
    protected void onDestroy() {
        if (liveListener != null) {
            liveListener.remove();
            liveListener = null;
        }
        super.onDestroy();
    }
}

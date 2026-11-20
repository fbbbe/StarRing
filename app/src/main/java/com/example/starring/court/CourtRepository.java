package com.example.starring.court;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 우주 재판장 데이터 저장소 (임시: 메모리 더미 데이터).
 *
 * DB(Firestore)가 정해지면 이 클래스 안쪽만 바꾸고,
 * 화면(Activity)들은 이 클래스의 메서드만 쓰도록 만들어 둠.
 * 앱을 껐다 켜면 데이터가 초기화됨.
 */
public final class CourtRepository {

    private static final long HOUR = 3600000L;
    private static final List<CourtPost> POSTS = new ArrayList<>();

    static {
        long now = System.currentTimeMillis();

        POSTS.add(new CourtPost(
                "dummy-1",
                "카톡 답장 지연",
                "약속 장소로 가는 동안 두 시간 동안 답장이 없었어요.",
                "바빠서 확인을 못 했을 뿐이라 서운해할 일은 아니에요.",
                "연락이 안 되면 걱정되니까 짧게라도 답장해 주면 좋겠어요.",
                "별빛은하계",
                40, 60,
                now - 2 * HOUR,
                now + 5 * HOUR));

        POSTS.add(new CourtPost(
                "dummy-2",
                "약속 시간 지각",
                "약속 시간에 30분 늦게 도착했어요.",
                "교통 상황 때문이라 어쩔 수 없었어요.",
                "미리 연락을 줬다면 기다리는 시간이 덜 힘들었을 거예요.",
                "달빛은하계",
                25, 75,
                now - 1 * HOUR,
                now + 2 * HOUR));
    }

    private CourtRepository() {
    }

    // latestFirst = true : 최신순 / false : 마감임박순
    public static synchronized List<CourtPost> getPosts(boolean latestFirst) {
        List<CourtPost> copy = new ArrayList<>(POSTS);
        if (latestFirst) {
            Collections.sort(copy, new Comparator<CourtPost>() {
                @Override
                public int compare(CourtPost a, CourtPost b) {
                    return Long.compare(b.createdAt, a.createdAt);
                }
            });
        } else {
            Collections.sort(copy, new Comparator<CourtPost>() {
                @Override
                public int compare(CourtPost a, CourtPost b) {
                    return Long.compare(a.deadlineAt, b.deadlineAt);
                }
            });
        }
        return copy;
    }

    public static synchronized CourtPost findById(String id) {
        for (CourtPost post : POSTS) {
            if (post.id.equals(id)) {
                return post;
            }
        }
        return null;
    }

    public static synchronized void add(CourtPost post) {
        POSTS.add(post);
    }

    // choice : CourtPost.VOTE_A 또는 CourtPost.VOTE_B (이미 투표했으면 무시)
    public static synchronized void vote(String id, int choice) {
        CourtPost post = findById(id);
        if (post == null || post.myVote != CourtPost.VOTE_NONE) {
            return;
        }
        if (choice == CourtPost.VOTE_A) {
            post.votesA++;
        } else if (choice == CourtPost.VOTE_B) {
            post.votesB++;
        } else {
            return;
        }
        post.myVote = choice;
    }

    // 내 배운점 제출. 상대도 이미 썼다면 이 순간 화해 완료(해결 시각 기록 -> 삼킨 별 반환)
    // TODO(DB 연동 후): 내 uid 기준으로 Firestore에 저장, 둘 다 있으면 resolvedAt 기록
    public static synchronized void submitMyLesson(String id, String lesson) {
        CourtPost post = findById(id);
        if (post == null || post.isResolved() || post.myLesson != null) {
            return;
        }
        post.myLesson = lesson;
        resolveIfBothWrote(post);
    }

    // [테스트용] 상대 폰에서 배운점을 쓴 것처럼 처리 (DB 연동 후 삭제)
    public static synchronized void submitPartnerLessonForTest(String id) {
        CourtPost post = findById(id);
        if (post == null || post.isResolved() || post.partnerLesson != null) {
            return;
        }
        post.partnerLesson = "(테스트) 상대방도 이번 일로 배운 점을 적었어요.";
        resolveIfBothWrote(post);
    }

    private static void resolveIfBothWrote(CourtPost post) {
        if (post.myLesson != null && post.partnerLesson != null && !post.isResolved()) {
            post.resolvedAt = System.currentTimeMillis();
        }
    }
}

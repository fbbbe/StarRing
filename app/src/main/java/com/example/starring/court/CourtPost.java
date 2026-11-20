package com.example.starring.court;

/**
 * 우주 재판장에 올라온 갈등(블랙홀) 한 건.
 * 나중에 Firestore 문서로 바로 매핑할 수 있도록 public 필드 + 기본 생성자를 둠.
 */
public class CourtPost {

    public static final int VOTE_NONE = 0;
    public static final int VOTE_A = 1;
    public static final int VOTE_B = 2;

    public String id;
    public String title;
    public String situation;
    public String positionA;
    public String positionB;
    public String galaxyName;
    public int votesA;
    public int votesB;
    public long createdAt;
    public long deadlineAt;
    public long resolvedAt;      // 화해한 시각 (0 = 아직 화해 전)
    public boolean mine;         // 내가 만든 블랙홀인지 (화해는 만든 사람만)
    public String myLesson;      // 내가 쓴 배운점 (null = 아직 안 씀)
    public String partnerLesson; // 상대가 쓴 배운점 (null = 아직 안 씀)

    // 내가 어느 쪽에 투표했는지 (익명 투표라 로컬 상태로만 관리)
    public int myVote = VOTE_NONE;

    // Firestore용 기본 생성자
    public CourtPost() {
    }

    public CourtPost(String id, String title, String situation,
                     String positionA, String positionB, String galaxyName,
                     int votesA, int votesB, long createdAt, long deadlineAt) {
        this.id = id;
        this.title = title;
        this.situation = situation;
        this.positionA = positionA;
        this.positionB = positionB;
        this.galaxyName = galaxyName;
        this.votesA = votesA;
        this.votesB = votesB;
        this.createdAt = createdAt;
        this.deadlineAt = deadlineAt;
    }

    public int totalVotes() {
        return votesA + votesB;
    }

    // 투표가 없으면 50 : 50 으로 보여줌
    public int percentA() {
        int total = totalVotes();
        if (total == 0) {
            return 50;
        }
        return Math.round(votesA * 100f / total);
    }

    public int percentB() {
        return 100 - percentA();
    }

    public long remainingMillis() {
        return deadlineAt - System.currentTimeMillis();
    }

    // 시간이 지났거나 화해로 해결된 글은 투표가 닫힘
    public boolean isExpired() {
        return isResolved() || remainingMillis() <= 0;
    }

    public boolean isResolved() {
        return resolvedAt > 0;
    }

    // 화해 완료 시점에 돌아온 별 개수 (화해한 시각까지 삼킨 만큼)
    public int returnedStars(int availableStars) {
        if (!isResolved()) {
            return 0;
        }
        return StarConsumption.swallowed(createdAt, 0, resolvedAt, availableStars);
    }

    // 지금 삼킨 별 개수 (availableStars = 은하계의 애기별 수)
    public int swallowedStars(int availableStars) {
        return StarConsumption.swallowed(createdAt, resolvedAt,
                System.currentTimeMillis(), availableStars);
    }

    // 마감 1시간 미만 -> 긴박 모드(붉은 깜빡임)
    public boolean isUrgent() {
        long left = remainingMillis();
        return left > 0 && left < 3600000L;
    }

    // "05:00:00" 형태 (시간은 99를 넘어도 그대로 표시)
    public String remainingClock() {
        long left = Math.max(0, remainingMillis());
        long totalSec = left / 1000L;
        long h = totalSec / 3600;
        long m = (totalSec / 60) % 60;
        long s = totalSec % 60;
        return String.format(java.util.Locale.US, "%02d:%02d:%02d", h, m, s);
    }

    // 목록/투표 화면 공용 문구
    public String remainingText() {
        if (isExpired()) {
            return "마감됨";
        }
        return "남은 시간 " + remainingClock();
    }
}

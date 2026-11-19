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

    // "남은 시간 3시간 20분" 형태
    public String remainingText() {
        long left = deadlineAt - System.currentTimeMillis();
        if (left <= 0) {
            return "마감됨";
        }
        long hours = left / 3600000L;
        long minutes = (left / 60000L) % 60;
        return "남은 시간 " + hours + "시간 " + minutes + "분";
    }
}

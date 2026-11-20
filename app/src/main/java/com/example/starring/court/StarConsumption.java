package com.example.starring.court;

/**
 * 블랙홀이 삼킨 별 계산 (저장하지 않고 시각으로 계산 - 팀 규칙).
 * - 블랙홀 생성 후 30분마다 애기별 1개씩 삼킴
 * - 화해(resolvedAt 기록)하면 삼킨 별이 모두 돌아옴 -> 0개
 * - 가진 별 수보다 많이 삼킬 수는 없음
 */
public final class StarConsumption {

    /** 별 1개를 삼키는 간격 (30분) */
    public static final long INTERVAL_MS = 30L * 60L * 1000L;

    private StarConsumption() {
    }

    /**
     * @param createdAt      블랙홀 생성 시각 (ms)
     * @param resolvedAt     화해 시각 (ms), 아직 화해 전이면 0
     * @param now            현재 시각 (ms)
     * @param availableStars 은하계가 가진 애기별 개수
     */
    public static int swallowed(long createdAt, long resolvedAt, long now, int availableStars) {
        if (resolvedAt > 0) {
            return 0; // 화해 완료 -> 별 반환
        }
        long elapsed = now - createdAt;
        if (elapsed < INTERVAL_MS) {
            return 0; // 첫 별은 30분 뒤 (기기/서버 시각 차이로 음수가 나와도 0)
        }
        long count = elapsed / INTERVAL_MS;
        return (int) Math.min(count, Math.max(0, availableStars));
    }

    /** 다음 별을 삼키기까지 남은 시간 (ms). 화해했으면 0 */
    public static long millisUntilNext(long createdAt, long resolvedAt, long now) {
        if (resolvedAt > 0) {
            return 0L;
        }
        long elapsed = now - createdAt;
        if (elapsed < 0) {
            return INTERVAL_MS;
        }
        return INTERVAL_MS - (elapsed % INTERVAL_MS);
    }
}

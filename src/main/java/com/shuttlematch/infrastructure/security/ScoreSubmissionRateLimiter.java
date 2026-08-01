package com.shuttlematch.infrastructure.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * ミニゲームのスコア登録に対する、送信元ごとの簡易レート制限。
 *
 * <p>認証が無いので誰でも登録できる。連投でランキングを1人に埋められるのを防ぐための
 * 軽い歯止めで、本気の改ざんを止めるものではない。単一インスタンス運用なのでメモリで持つ
 * (再起動で消えてよい程度の情報)。
 */
@Component
public class ScoreSubmissionRateLimiter {

    /** 同じ送信元からの登録の最短間隔。1プレイに必ずこれ以上かかる。 */
    private static final Duration MIN_INTERVAL = Duration.ofSeconds(10);

    /**
     * 記憶しておく送信元の上限。超えたら丸ごと捨てる(古い順に消す仕組みを持つより単純で、
     * 捨てても「次の1回が通る」だけなので実害がない)。
     */
    private static final int MAX_TRACKED = 10_000;

    private final Map<String, Instant> lastSubmittedAt = new ConcurrentHashMap<>();
    private final Clock clock;

    public ScoreSubmissionRateLimiter(Clock clock) {
        this.clock = clock;
    }

    /**
     * 登録を受け付けてよいか。受け付ける場合はその時刻を記録する。
     *
     * @param clientKey 送信元の識別子(IP アドレスなど)
     */
    public boolean tryAcquire(String clientKey) {
        Instant now = clock.instant();
        if (lastSubmittedAt.size() >= MAX_TRACKED) {
            lastSubmittedAt.clear();
        }
        Instant previous = lastSubmittedAt.putIfAbsent(clientKey, now);
        if (previous == null) {
            return true;
        }
        if (Duration.between(previous, now).compareTo(MIN_INTERVAL) < 0) {
            return false;
        }
        // 直近の登録から十分空いていれば通す。競合しても「1回余分に通る」だけ。
        lastSubmittedAt.put(clientKey, now);
        return true;
    }
}

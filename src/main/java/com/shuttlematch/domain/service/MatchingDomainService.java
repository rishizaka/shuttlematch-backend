package com.shuttlematch.domain.service;

import com.shuttlematch.domain.model.match.Match;
import com.shuttlematch.domain.model.match.MatchNumber;
import com.shuttlematch.domain.model.match.MatchSchedule;
import com.shuttlematch.domain.model.match.Pair;
import com.shuttlematch.domain.model.session.ParticipantId;
import com.shuttlematch.domain.model.session.SessionId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * ダブルスのランダムマッチングを行うドメインサービス。
 * <p>
 * 方針:
 * <ul>
 *   <li>各試合の4名は「出場回数が少ない順」に選ぶ。これにより参加者全員の
 *       出場回数が均等になる(奇数人数時の輪番もこの仕組みで自然に実現される)。</li>
 *   <li>タイブレークおよびペア分けは Fisher-Yates シャッフルでランダム化する。</li>
 *   <li>直前の試合と同一カードが連続しないようベストエフォートで回避する。</li>
 * </ul>
 * 乱数生成器を注入できるため、テストではシード固定で決定的に検証できる。
 */
public class MatchingDomainService {

    /** ダブルス1試合に必要な最低人数。 */
    public static final int MIN_PARTICIPANTS = 4;
    /** 1セッションあたりのデフォルト試合数。 */
    public static final int DEFAULT_MATCH_COUNT = 15;
    /** 同一カード連続を避けるための再シャッフル試行回数の上限。 */
    private static final int MAX_RESHUFFLE_ATTEMPTS = 10;
    /** 1試合あたりの人数(ダブルス)。 */
    private static final int PLAYERS_PER_MATCH = 4;

    private final RandomGenerator random;

    public MatchingDomainService() {
        this(new Random());
    }

    public MatchingDomainService(RandomGenerator random) {
        this.random = Objects.requireNonNull(random, "random は null にできません");
    }

    /** デフォルト試合数(15)で生成する。 */
    public MatchSchedule generate(SessionId sessionId, List<ParticipantId> participants) {
        return generate(sessionId, participants, DEFAULT_MATCH_COUNT);
    }

    public MatchSchedule generate(SessionId sessionId, List<ParticipantId> participants, int matchCount) {
        Objects.requireNonNull(sessionId, "sessionId は必須です");
        Objects.requireNonNull(participants, "participants は必須です");
        if (matchCount < 1) {
            throw new IllegalArgumentException("試合数は1以上である必要があります: " + matchCount);
        }

        // 重複参加者を除去(登録ユーザーの重複は本来上流で防がれるが念のため)
        List<ParticipantId> pool = new ArrayList<>(new LinkedHashSet<>(participants));
        if (pool.size() < MIN_PARTICIPANTS) {
            throw new IllegalArgumentException(
                    "ダブルスの試合を生成するには最低 " + MIN_PARTICIPANTS + " 人必要です (現在 " + pool.size() + " 人)");
        }

        Map<ParticipantId, Integer> playCount = new HashMap<>();
        pool.forEach(p -> playCount.put(p, 0));

        List<Match> matches = new ArrayList<>(matchCount);
        Set<Pair> previousCard = null;

        for (int number = 1; number <= matchCount; number++) {
            List<ParticipantId> four = pickLeastPlayed(pool, playCount);
            Match match = buildMatch(number, four, previousCard);

            matches.add(match);
            previousCard = match.pairs();
            four.forEach(p -> playCount.merge(p, 1, Integer::sum));
        }

        return new MatchSchedule(sessionId, matches);
    }

    /** 出場回数が少ない順に4名を選ぶ(同回数同士はランダムにタイブレーク)。 */
    private List<ParticipantId> pickLeastPlayed(List<ParticipantId> pool, Map<ParticipantId, Integer> playCount) {
        List<ParticipantId> candidates = new ArrayList<>(pool);
        fisherYatesShuffle(candidates);                              // タイブレークをランダム化
        candidates.sort(Comparator.comparingInt(playCount::get));    // 出場回数の昇順(安定ソート)
        return new ArrayList<>(candidates.subList(0, PLAYERS_PER_MATCH));
    }

    /** 4名をシャッフルして2ペアに分ける。直前と同一カードなら数回まで組み替える。 */
    private Match buildMatch(int number, List<ParticipantId> four, Set<Pair> previousCard) {
        List<ParticipantId> arrangement = new ArrayList<>(four);
        Match candidate = null;
        for (int attempt = 0; attempt < MAX_RESHUFFLE_ATTEMPTS; attempt++) {
            fisherYatesShuffle(arrangement);
            Pair pairA = new Pair(arrangement.get(0), arrangement.get(1));
            Pair pairB = new Pair(arrangement.get(2), arrangement.get(3));
            candidate = Match.of(MatchNumber.of(number), pairA, pairB);
            if (previousCard == null || !candidate.pairs().equals(previousCard)) {
                return candidate;
            }
        }
        // ベストエフォート: 回避しきれない場合は最後の候補を採用
        return candidate;
    }

    private void fisherYatesShuffle(List<?> list) {
        for (int i = list.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            Collections.swap(list, i, j);
        }
    }
}

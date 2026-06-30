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
import java.util.random.RandomGenerator;

/**
 * ダブルスのランダムマッチングを行うドメインサービス。
 * <p>
 * コート数とセット数に応じて試合を生成する。1セットはコート数分の試合を持ち、
 * 各コートで4名が同時にプレーする(同じ人が同じセット内で2コートに出ることはない)。
 * <ul>
 *   <li>各セットの出場者は「出場回数が少ない順」に 4×コート数 名を選ぶ(公平な輪番)。</li>
 *   <li>タイブレーク・ペア分けは Fisher-Yates シャッフルでランダム化する。</li>
 * </ul>
 */
public class MatchingDomainService {

    /** ダブルス1試合の人数。 */
    private static final int PLAYERS_PER_MATCH = 4;
    /** デフォルトのセット数。 */
    public static final int DEFAULT_SET_COUNT = 10;

    private final RandomGenerator random;

    public MatchingDomainService() {
        this(new Random());
    }

    public MatchingDomainService(RandomGenerator random) {
        this.random = Objects.requireNonNull(random, "random は null にできません");
    }

    /**
     * 試合を生成する。
     *
     * @param courtCount コート数(1以上)
     * @param setCount   セット数(1以上)
     */
    public MatchSchedule generate(
            SessionId sessionId, List<ParticipantId> participants, int courtCount, int setCount) {
        Objects.requireNonNull(sessionId, "sessionId は必須です");
        Objects.requireNonNull(participants, "participants は必須です");
        if (courtCount < 1) {
            throw new IllegalArgumentException("コート数は1以上である必要があります: " + courtCount);
        }
        if (setCount < 1) {
            throw new IllegalArgumentException("セット数は1以上である必要があります: " + setCount);
        }

        List<ParticipantId> pool = new ArrayList<>(new LinkedHashSet<>(participants));
        int required = PLAYERS_PER_MATCH * courtCount;
        if (pool.size() < required) {
            throw new IllegalArgumentException(
                    "コート数 " + courtCount + " の試合には最低 " + required + " 人必要です (現在 " + pool.size() + " 人)");
        }

        Map<ParticipantId, Integer> playCount = new HashMap<>();
        pool.forEach(p -> playCount.put(p, 0));

        List<Match> matches = new ArrayList<>();
        int matchNumber = 1;

        for (int setNumber = 1; setNumber <= setCount; setNumber++) {
            List<ParticipantId> selected = pickLeastPlayed(pool, playCount, required);
            fisherYatesShuffle(selected); // セット内のコート割り・ペア分けをランダム化

            for (int court = 1; court <= courtCount; court++) {
                int base = (court - 1) * PLAYERS_PER_MATCH;
                Pair pairA = new Pair(selected.get(base), selected.get(base + 1));
                Pair pairB = new Pair(selected.get(base + 2), selected.get(base + 3));
                matches.add(Match.of(MatchNumber.of(matchNumber++), setNumber, court, pairA, pairB));
            }
            selected.forEach(p -> playCount.merge(p, 1, Integer::sum));
        }

        return new MatchSchedule(sessionId, matches);
    }

    /** 出場回数が少ない順に n 名を選ぶ(同回数同士はランダムにタイブレーク)。 */
    private List<ParticipantId> pickLeastPlayed(
            List<ParticipantId> pool, Map<ParticipantId, Integer> playCount, int n) {
        List<ParticipantId> candidates = new ArrayList<>(pool);
        fisherYatesShuffle(candidates);
        candidates.sort(Comparator.comparingInt(playCount::get));
        return new ArrayList<>(candidates.subList(0, n));
    }

    private void fisherYatesShuffle(List<?> list) {
        for (int i = list.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            Collections.swap(list, i, j);
        }
    }
}

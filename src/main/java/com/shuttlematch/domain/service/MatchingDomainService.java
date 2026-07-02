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
        requireCourtCount(courtCount);
        if (setCount < 1) {
            throw new IllegalArgumentException("セット数は1以上である必要があります: " + setCount);
        }

        List<ParticipantId> pool = validatedPool(participants, courtCount);
        Map<ParticipantId, Integer> playCount = new HashMap<>();
        pool.forEach(p -> playCount.put(p, 0));

        List<Match> matches = buildSets(pool, playCount, courtCount, setCount, 1, 1);
        return new MatchSchedule(sessionId, matches);
    }

    /**
     * 既存スケジュールにセットを追加する。出場回数・セット番号・試合番号は既存の状態から継続し、
     * 追加分も公平な輪番になるようにする。既存の試合(開始時刻を含む)はそのまま保持される。
     *
     * @param existing          追加元の既存スケジュール
     * @param additionalSetCount 追加するセット数(1以上)
     */
    public MatchSchedule addSets(
            MatchSchedule existing, List<ParticipantId> participants,
            int courtCount, int additionalSetCount) {
        Objects.requireNonNull(existing, "existing は必須です");
        requireCourtCount(courtCount);
        if (additionalSetCount < 1) {
            throw new IllegalArgumentException("追加セット数は1以上である必要があります: " + additionalSetCount);
        }

        List<ParticipantId> pool = validatedPool(participants, courtCount);

        // 既存の出場回数を集計して輪番を継続する(現在の参加者に含まれる分のみ)。
        Map<ParticipantId, Integer> playCount = new HashMap<>();
        pool.forEach(p -> playCount.put(p, 0));
        for (Match m : existing.matches()) {
            for (ParticipantId p : participantsOf(m)) {
                playCount.computeIfPresent(p, (key, count) -> count + 1);
            }
        }

        int startSetNumber = existing.setCount() + 1;
        int startMatchNumber = existing.matches().stream()
                .mapToInt(m -> m.matchNumber().value())
                .max()
                .orElse(0) + 1;

        List<Match> added =
                buildSets(pool, playCount, courtCount, additionalSetCount, startSetNumber, startMatchNumber);

        List<Match> all = new ArrayList<>(existing.matches());
        all.addAll(added);
        return new MatchSchedule(existing.sessionId(), all);
    }

    /**
     * playCount を消費しながら setCount 分のセットを組み立てる。
     * setNumber は startSetNumber から、matchNumber は startMatchNumber から連番で振る。
     */
    private List<Match> buildSets(
            List<ParticipantId> pool, Map<ParticipantId, Integer> playCount,
            int courtCount, int setCount, int startSetNumber, int startMatchNumber) {
        int required = PLAYERS_PER_MATCH * courtCount;
        List<Match> matches = new ArrayList<>();
        int matchNumber = startMatchNumber;

        for (int i = 0; i < setCount; i++) {
            int setNumber = startSetNumber + i;
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
        return matches;
    }

    private void requireCourtCount(int courtCount) {
        if (courtCount < 1) {
            throw new IllegalArgumentException("コート数は1以上である必要があります: " + courtCount);
        }
    }

    /** 参加者を重複排除し、コート数に対して人数が足りているか検証したプールを返す。 */
    private List<ParticipantId> validatedPool(List<ParticipantId> participants, int courtCount) {
        Objects.requireNonNull(participants, "participants は必須です");
        List<ParticipantId> pool = new ArrayList<>(new LinkedHashSet<>(participants));
        int required = PLAYERS_PER_MATCH * courtCount;
        if (pool.size() < required) {
            throw new IllegalArgumentException(
                    "コート数 " + courtCount + " の試合には最低 " + required + " 人必要です (現在 " + pool.size() + " 人)");
        }
        return pool;
    }

    /** 1試合に出場する4名の ParticipantId。 */
    private List<ParticipantId> participantsOf(Match match) {
        return List.of(
                match.pairA().player1(), match.pairA().player2(),
                match.pairB().player1(), match.pairB().player2());
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

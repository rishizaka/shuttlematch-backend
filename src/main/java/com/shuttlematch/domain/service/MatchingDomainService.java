package com.shuttlematch.domain.service;

import com.shuttlematch.domain.model.match.Match;
import com.shuttlematch.domain.model.match.MatchNumber;
import com.shuttlematch.domain.model.match.MatchSchedule;
import com.shuttlematch.domain.model.match.Pair;
import com.shuttlematch.domain.model.room.ParticipantId;
import com.shuttlematch.domain.model.room.RoomId;

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
 *   <li>出場回数が同じなら「最後に出場したセットが古い順」(＝長く休んでいる人)を優先し、
 *       同一人物が連続で休みにならないようにする。</li>
 *   <li>それでも同順位ならタイブレーク・ペア分けを Fisher-Yates シャッフルでランダム化する。</li>
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
            RoomId roomId, List<ParticipantId> participants, int courtCount, int setCount) {
        Objects.requireNonNull(roomId, "roomId は必須です");
        requireCourtCount(courtCount);
        if (setCount < 1) {
            throw new IllegalArgumentException("セット数は1以上である必要があります: " + setCount);
        }

        List<ParticipantId> pool = validatedPool(participants, courtCount);
        Map<ParticipantId, Integer> playCount = new HashMap<>();
        pool.forEach(p -> playCount.put(p, 0));
        // 0 = まだ一度も出場していない(全員同条件でスタート)。
        Map<ParticipantId, Integer> lastPlayedSet = new HashMap<>();
        pool.forEach(p -> lastPlayedSet.put(p, 0));

        List<Match> matches = buildSets(pool, playCount, lastPlayedSet, courtCount, setCount, 1, 1);
        return new MatchSchedule(roomId, matches);
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

        // 既存の出場回数を引き継ぐ。新規参加者は優先させない(既存の最小回数にシード)。
        Map<ParticipantId, Integer> playCount = seededPlayCounts(pool, existing.matches());
        // 「最後に出場したセット」も引き継ぐ。連続休み回避が既存セットをまたいで効くようにする。
        Map<ParticipantId, Integer> lastPlayedSet =
                seededLastPlayedSet(pool, existing.matches(), existing.setCount());

        int startSetNumber = existing.setCount() + 1;
        int startMatchNumber = maxMatchNumber(existing.matches()) + 1;

        List<Match> added = buildSets(
                pool, playCount, lastPlayedSet, courtCount, additionalSetCount,
                startSetNumber, startMatchNumber);

        List<Match> all = new ArrayList<>(existing.matches());
        all.addAll(added);
        return new MatchSchedule(existing.roomId(), all);
    }

    /**
     * 既存スケジュールの未開始セットを、現在の在席者で作り直す(途中参加・早退への対応)。
     * <ul>
     *   <li>開始済みセットは履歴として不変。ここには一切手を加えない。</li>
     *   <li>未開始セットは破棄し、開始済みの出場実績を引き継いで作り直す。</li>
     *   <li>新規(途中参加)は優先させない。既存の最小回数にシードして横入りさせる。</li>
     *   <li>在席者が少なくコートを埋められない場合は、未来のコート数を自動で減らす。</li>
     * </ul>
     * 合計セット数は元のスケジュールと同じに保つ。
     *
     * @param existing            既存スケジュール
     * @param activeParticipants  現在の在席者(早退者は含めない)
     * @param courtCount          セッションの本来のコート数(1以上)
     */
    public MatchSchedule replanFuture(
            MatchSchedule existing, List<ParticipantId> activeParticipants, int courtCount) {
        Objects.requireNonNull(existing, "existing は必須です");
        Objects.requireNonNull(activeParticipants, "activeParticipants は必須です");
        requireCourtCount(courtCount);

        int maxStartedSet = existing.matches().stream()
                .filter(Match::isStarted)
                .mapToInt(Match::setNumber)
                .max()
                .orElse(0);
        List<Match> committed = existing.matches().stream()
                .filter(m -> m.setNumber() <= maxStartedSet)
                .toList();

        int futureSetCount = existing.setCount() - maxStartedSet;
        List<ParticipantId> pool = new ArrayList<>(new LinkedHashSet<>(activeParticipants));
        // 在席人数で埋められるコート数まで自動で縮小する。
        int effectiveCourtCount = Math.min(courtCount, pool.size() / PLAYERS_PER_MATCH);

        if (futureSetCount <= 0 || effectiveCourtCount < 1) {
            // 未開始セットが無い、または人数不足で組めない場合は確定分のみ残す。
            return new MatchSchedule(existing.roomId(), committed);
        }

        Map<ParticipantId, Integer> playCount = seededPlayCounts(pool, committed);
        Map<ParticipantId, Integer> lastPlayedSet =
                seededLastPlayedSet(pool, committed, maxStartedSet);
        List<Match> future = buildSets(
                pool, playCount, lastPlayedSet, effectiveCourtCount, futureSetCount,
                maxStartedSet + 1, maxMatchNumber(committed) + 1);

        List<Match> all = new ArrayList<>(committed);
        all.addAll(future);
        return new MatchSchedule(existing.roomId(), all);
    }

    /**
     * 参照試合から出場回数を集計する。プールに居るが未出場の参加者(途中参加など)は
     * 「既に出ている人の最小回数」にシードして、優先(キャッチアップ)させない。
     */
    private Map<ParticipantId, Integer> seededPlayCounts(
            List<ParticipantId> pool, List<Match> sourceMatches) {
        Map<ParticipantId, Integer> appeared = new HashMap<>();
        for (Match m : sourceMatches) {
            for (ParticipantId p : participantsOf(m)) {
                appeared.merge(p, 1, Integer::sum);
            }
        }
        int baseline = pool.stream()
                .filter(appeared::containsKey)
                .mapToInt(appeared::get)
                .min()
                .orElse(0);
        Map<ParticipantId, Integer> playCount = new HashMap<>();
        for (ParticipantId p : pool) {
            playCount.put(p, appeared.getOrDefault(p, baseline));
        }
        return playCount;
    }

    /**
     * 参照試合から各参加者が「最後に出場したセット番号」を集計する。
     * 連続休み回避のソートキーに使う(値が小さいほど長く休んでいる=優先出場)。
     * プールに居るが未出場の参加者(途中参加など)は、既に居た人より休憩が長い扱いに
     * ならないよう {@code fallbackSetNumber}(直前セット)にシードして横入りさせる。
     */
    private Map<ParticipantId, Integer> seededLastPlayedSet(
            List<ParticipantId> pool, List<Match> sourceMatches, int fallbackSetNumber) {
        Map<ParticipantId, Integer> lastPlayed = new HashMap<>();
        for (Match m : sourceMatches) {
            for (ParticipantId p : participantsOf(m)) {
                lastPlayed.merge(p, m.setNumber(), Math::max);
            }
        }
        Map<ParticipantId, Integer> result = new HashMap<>();
        for (ParticipantId p : pool) {
            result.put(p, lastPlayed.getOrDefault(p, fallbackSetNumber));
        }
        return result;
    }

    private int maxMatchNumber(List<Match> matches) {
        return matches.stream().mapToInt(m -> m.matchNumber().value()).max().orElse(0);
    }

    /**
     * playCount / lastPlayedSet を消費しながら setCount 分のセットを組み立てる。
     * setNumber は startSetNumber から、matchNumber は startMatchNumber から連番で振る。
     */
    private List<Match> buildSets(
            List<ParticipantId> pool, Map<ParticipantId, Integer> playCount,
            Map<ParticipantId, Integer> lastPlayedSet,
            int courtCount, int setCount, int startSetNumber, int startMatchNumber) {
        int required = PLAYERS_PER_MATCH * courtCount;
        List<Match> matches = new ArrayList<>();
        int matchNumber = startMatchNumber;

        for (int i = 0; i < setCount; i++) {
            int setNumber = startSetNumber + i;
            List<ParticipantId> selected = pickLeastPlayed(pool, playCount, lastPlayedSet, required);
            fisherYatesShuffle(selected); // セット内のコート割り・ペア分けをランダム化

            for (int court = 1; court <= courtCount; court++) {
                int base = (court - 1) * PLAYERS_PER_MATCH;
                Pair pairA = new Pair(selected.get(base), selected.get(base + 1));
                Pair pairB = new Pair(selected.get(base + 2), selected.get(base + 3));
                matches.add(Match.of(MatchNumber.of(matchNumber++), setNumber, court, pairA, pairB));
            }
            for (ParticipantId p : selected) {
                playCount.merge(p, 1, Integer::sum);
                lastPlayedSet.put(p, setNumber); // 出場したセットを記録(休みの連続長の判定に使う)
            }
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

    /**
     * 出場者 n 名を選ぶ。優先度は
     * (1) 出場回数が少ない順(公平性)、(2) 最後に出場したセットが古い順(＝長く休んでいる人・連続休み回避)、
     * (3) ランダム(先頭シャッフルによる最終タイブレーク)。
     */
    private List<ParticipantId> pickLeastPlayed(
            List<ParticipantId> pool, Map<ParticipantId, Integer> playCount,
            Map<ParticipantId, Integer> lastPlayedSet, int n) {
        List<ParticipantId> candidates = new ArrayList<>(pool);
        fisherYatesShuffle(candidates);
        candidates.sort(Comparator.comparingInt((ParticipantId p) -> playCount.get(p))
                .thenComparingInt(p -> lastPlayedSet.get(p)));
        return new ArrayList<>(candidates.subList(0, n));
    }

    private void fisherYatesShuffle(List<?> list) {
        for (int i = list.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            Collections.swap(list, i, j);
        }
    }
}

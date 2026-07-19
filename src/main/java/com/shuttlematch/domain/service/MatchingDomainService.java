package com.shuttlematch.domain.service;

import com.shuttlematch.domain.model.match.Match;
import com.shuttlematch.domain.model.match.MatchNumber;
import com.shuttlematch.domain.model.match.MatchSchedule;
import com.shuttlematch.domain.model.match.Pair;
import com.shuttlematch.domain.model.room.ParticipantId;
import com.shuttlematch.domain.model.room.RoomId;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
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
 * コート数とセット数に応じて試合を生成する。1セットはコート数分の試合を持ち、
 * 各コートで4名が同時にプレーする(同じ人が同じセット内で2コートに出ることはない)。
 * <ul>
 *   <li>各セットの出場者は「出場回数が少ない順」に 4×コート数 名を選ぶ(公平な輪番)。
 *       出場回数の差は常に最大1に保たれる。</li>
 *   <li>出場回数が同じなら「連続出場が少ない順」に出場させる(＝長く連続でコートに入って
 *       いる人を優先して休ませる)。身体ケアのため、公平性を保ったまま長い連続出場を避ける。
 *       直前に休んだ人は連続0で優先出場になるので、同一人物が連続で休みにもならない。</li>
 *   <li>それでも同順位ならタイブレーク・ペア分けを Fisher-Yates シャッフルでランダム化する。</li>
 * </ul>
 * <p>
 * <b>固定ペア</b>: 事前に「常に同じチームで組む2人」を指定できる。固定ペアは1つの
 * 「ユニット」として扱われ、常に一緒に出場・休憩し、同じ {@link Pair} に配置される。
 * 固定ペアが無い場合は各参加者が1人ユニットとなり、従来どおりの個人単位の挙動になる。
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

    /** 固定ペア無しで生成する。 */
    public MatchSchedule generate(
            RoomId roomId, List<ParticipantId> participants, int courtCount, int setCount) {
        return generate(roomId, participants, courtCount, setCount, List.of());
    }

    /**
     * 試合を生成する。
     *
     * @param courtCount コート数(1以上)
     * @param setCount   セット数(1以上)
     * @param fixedPairs 常に同じチームで組む固定ペア(空可)
     */
    public MatchSchedule generate(
            RoomId roomId, List<ParticipantId> participants, int courtCount, int setCount,
            List<Pair> fixedPairs) {
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
        // 連続出場数(何セット連続でコートに入っているか)。休むと0にリセット。
        // 身体ケアのため、公平性を保ちつつ長い連続出場を避けるのに使う。
        Map<ParticipantId, Integer> consecutivePlays = new HashMap<>();
        pool.forEach(p -> consecutivePlays.put(p, 0));

        List<Match> matches = buildSets(
                pool, playCount, lastPlayedSet, consecutivePlays,
                courtCount, setCount, 1, 1, fixedPairs, List.of());
        return new MatchSchedule(roomId, matches);
    }

    /** 固定ペア無しでセットを追加する。 */
    public MatchSchedule addSets(
            MatchSchedule existing, List<ParticipantId> participants,
            int courtCount, int additionalSetCount) {
        return addSets(existing, participants, courtCount, additionalSetCount, List.of());
    }

    /**
     * 既存スケジュールにセットを追加する。出場回数・セット番号・試合番号は既存の状態から継続し、
     * 追加分も公平な輪番になるようにする。既存の試合(開始時刻を含む)はそのまま保持される。
     *
     * @param existing           追加元の既存スケジュール
     * @param additionalSetCount 追加するセット数(1以上)
     * @param fixedPairs         常に同じチームで組む固定ペア(空可)
     */
    public MatchSchedule addSets(
            MatchSchedule existing, List<ParticipantId> participants,
            int courtCount, int additionalSetCount, List<Pair> fixedPairs) {
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
        // 連続出場数も引き継ぐ(連続出場の抑制が既存セットをまたいで効くようにする)。
        Map<ParticipantId, Integer> consecutivePlays =
                seededConsecutivePlays(pool, existing.matches(), existing.setCount());

        int startSetNumber = existing.setCount() + 1;
        int startMatchNumber = maxMatchNumber(existing.matches()) + 1;

        List<Match> added = buildSets(
                pool, playCount, lastPlayedSet, consecutivePlays, courtCount, additionalSetCount,
                startSetNumber, startMatchNumber, fixedPairs,
                restGroupsOf(pool, existing.matches()));

        List<Match> all = new ArrayList<>(existing.matches());
        all.addAll(added);
        return new MatchSchedule(existing.roomId(), all);
    }

    /** 固定ペア無しで未開始セットを再編成する。 */
    public MatchSchedule replanFuture(
            MatchSchedule existing, List<ParticipantId> activeParticipants, int courtCount) {
        return replanFuture(existing, activeParticipants, courtCount, List.of());
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
     * @param existing           既存スケジュール
     * @param activeParticipants 現在の在席者(早退者は含めない)
     * @param courtCount         セッションの本来のコート数(1以上)
     * @param fixedPairs         常に同じチームで組む固定ペア(空可)
     */
    public MatchSchedule replanFuture(
            MatchSchedule existing, List<ParticipantId> activeParticipants, int courtCount,
            List<Pair> fixedPairs) {
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
        Map<ParticipantId, Integer> consecutivePlays =
                seededConsecutivePlays(pool, committed, maxStartedSet);
        List<Match> future = buildSets(
                pool, playCount, lastPlayedSet, consecutivePlays, effectiveCourtCount, futureSetCount,
                maxStartedSet + 1, maxMatchNumber(committed) + 1, fixedPairs,
                restGroupsOf(pool, committed));

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

    /**
     * 参照試合から各参加者の「直近の連続出場数」を集計する({@code lastSet} まで遡り、
     * 出場が途切れる直前までの連続数)。連続出場の抑制を既存セットをまたいで効かせるのに使う。
     * プールに居るが未出場の参加者は 0(連続なし)。
     */
    private Map<ParticipantId, Integer> seededConsecutivePlays(
            List<ParticipantId> pool, List<Match> sourceMatches, int lastSet) {
        Map<Integer, Set<ParticipantId>> playersBySet = new HashMap<>();
        for (Match m : sourceMatches) {
            playersBySet
                    .computeIfAbsent(m.setNumber(), k -> new HashSet<>())
                    .addAll(participantsOf(m));
        }
        Map<ParticipantId, Integer> streak = new HashMap<>();
        for (ParticipantId p : pool) {
            int s = 0;
            for (int set = lastSet; set >= 1; set--) {
                if (playersBySet.getOrDefault(set, Set.of()).contains(p)) {
                    s++;
                } else {
                    break;
                }
            }
            streak.put(p, s);
        }
        return streak;
    }

    private int maxMatchNumber(List<Match> matches) {
        return matches.stream().mapToInt(m -> m.matchNumber().value()).max().orElse(0);
    }

    /**
     * 参照試合から各セットの「休み(現プールに居るが出場していない人)の組」をセット順に返す。
     * セット追加・再編成のときに休みグループ多様化の履歴を引き継ぐのに使う。
     */
    private List<Set<ParticipantId>> restGroupsOf(
            List<ParticipantId> pool, List<Match> sourceMatches) {
        Map<Integer, Set<ParticipantId>> playingBySet = new HashMap<>();
        for (Match m : sourceMatches) {
            playingBySet
                    .computeIfAbsent(m.setNumber(), k -> new HashSet<>())
                    .addAll(participantsOf(m));
        }
        Set<ParticipantId> poolSet = new HashSet<>(pool);
        List<Integer> sets = new ArrayList<>(playingBySet.keySet());
        Collections.sort(sets);
        List<Set<ParticipantId>> result = new ArrayList<>();
        for (int s : sets) {
            Set<ParticipantId> rest = new HashSet<>(poolSet);
            rest.removeAll(playingBySet.get(s));
            result.add(rest);
        }
        return result;
    }

    /**
     * playCount / lastPlayedSet を消費しながら setCount 分のセットを組み立てる。
     * setNumber は startSetNumber から、matchNumber は startMatchNumber から連番で振る。
     * <p>
     * 参加者は「ユニット」に分けて扱う。固定ペア(pool に両方居るもの)は2人1組のユニット、
     * それ以外は1人ユニット。出場者選択・休憩の反復回避はユニット単位で行い、固定ペアが
     * 常に一緒に出入りし同じ Pair に入るようにする。
     */
    private List<Match> buildSets(
            List<ParticipantId> pool, Map<ParticipantId, Integer> playCount,
            Map<ParticipantId, Integer> lastPlayedSet,
            Map<ParticipantId, Integer> consecutivePlays,
            int courtCount, int setCount, int startSetNumber, int startMatchNumber,
            List<Pair> fixedPairs, List<Set<ParticipantId>> seedRecentRest) {
        int required = PLAYERS_PER_MATCH * courtCount;
        List<List<ParticipantId>> units = buildUnits(pool, fixedPairs);
        boolean hasRest = pool.size() > required;

        List<Match> matches = new ArrayList<>();
        int matchNumber = startMatchNumber;

        // 直近セットの「休んだメンバーの組」を覚えておき、同じ組の反復を避ける(カップリング防止)。
        Deque<Set<ParticipantId>> recentRest = new ArrayDeque<>();
        int restWindow = Math.max(1, units.size() - 1);
        // セット追加・再編成のときは、確定済みセットの直近の休みグループ履歴を引き継ぐ。
        // これで境界をまたいでも同じ組の反復回避(多様化)が継続する。
        int seedFrom = Math.max(0, seedRecentRest.size() - restWindow);
        for (int k = seedFrom; k < seedRecentRest.size(); k++) {
            recentRest.addLast(seedRecentRest.get(k));
        }

        for (int i = 0; i < setCount; i++) {
            int setNumber = startSetNumber + i;
            List<List<ParticipantId>> selectedUnits =
                    pickUnits(units, playCount, lastPlayedSet, consecutivePlays, required);
            if (hasRest) {
                selectedUnits = avoidRepeatedRest(
                        units, selectedUnits, playCount, lastPlayedSet, setNumber, recentRest);
                Set<ParticipantId> rest = restMembers(units, selectedUnits);
                recentRest.addLast(rest);
                while (recentRest.size() > restWindow) recentRest.removeFirst();
            }

            List<Pair> pairs = buildPairs(selectedUnits); // 固定ペアはそのまま、自由参加者は2人ずつ
            fisherYatesShuffle(pairs); // コート割り・pairA/pairB をランダム化
            for (int court = 1; court <= courtCount; court++) {
                Pair pairA = pairs.get((court - 1) * 2);
                Pair pairB = pairs.get((court - 1) * 2 + 1);
                matches.add(Match.of(MatchNumber.of(matchNumber++), setNumber, court, pairA, pairB));
            }

            Set<ParticipantId> playing = new HashSet<>();
            for (List<ParticipantId> unit : selectedUnits) {
                for (ParticipantId p : unit) {
                    playCount.merge(p, 1, Integer::sum);
                    lastPlayedSet.put(p, setNumber); // 出場したセットを記録
                    consecutivePlays.merge(p, 1, Integer::sum); // 連続出場を+1
                    playing.add(p);
                }
            }
            // 休んだ人は連続出場をリセット。
            for (ParticipantId p : pool) {
                if (!playing.contains(p)) consecutivePlays.put(p, 0);
            }
        }
        return matches;
    }

    /**
     * 参加者を「ユニット」に分ける。pool に両方が居る固定ペアは2人ユニット、
     * それ以外は1人ユニット。1人が複数ペアに属さないよう、先に現れたペアを優先する。
     */
    private List<List<ParticipantId>> buildUnits(List<ParticipantId> pool, List<Pair> fixedPairs) {
        Set<ParticipantId> poolSet = new HashSet<>(pool);
        List<List<ParticipantId>> units = new ArrayList<>();
        Set<ParticipantId> paired = new HashSet<>();
        for (Pair p : fixedPairs) {
            if (poolSet.contains(p.player1()) && poolSet.contains(p.player2())
                    && !paired.contains(p.player1()) && !paired.contains(p.player2())) {
                units.add(List.of(p.player1(), p.player2()));
                paired.add(p.player1());
                paired.add(p.player2());
            }
        }
        for (ParticipantId id : pool) {
            if (!paired.contains(id)) units.add(List.of(id));
        }
        return units;
    }

    /**
     * 出場するユニットを選ぶ。優先度は
     * (1) 出場回数が少ない順、(2) 最後に出場したセットが古い順、(3) ランダム。
     * ユニットの合計人数がちょうど {@code required} になるよう選ぶ。
     * 端数(残り1人)が出た場合は、自由参加者1人を外して固定ペア1組を入れて帳尻を合わせる。
     */
    private List<List<ParticipantId>> pickUnits(
            List<List<ParticipantId>> units, Map<ParticipantId, Integer> playCount,
            Map<ParticipantId, Integer> lastPlayedSet,
            Map<ParticipantId, Integer> consecutivePlays, int required) {
        List<List<ParticipantId>> candidates = new ArrayList<>(units);
        fisherYatesShuffle(candidates);
        candidates.sort(unitOrder(playCount, consecutivePlays));

        List<List<ParticipantId>> selected = new ArrayList<>();
        List<List<ParticipantId>> skipped = new ArrayList<>();
        int remaining = required;
        for (List<ParticipantId> unit : candidates) {
            if (remaining >= unit.size()) {
                selected.add(unit);
                remaining -= unit.size();
            } else {
                skipped.add(unit);
            }
        }

        // 固定ペア(2人)ばかりで枠が1余った場合: 自由1人を外し、未出場の固定ペアを入れる。
        if (remaining == 1) {
            List<ParticipantId> freeToDrop = null;
            for (int k = selected.size() - 1; k >= 0; k--) {
                if (selected.get(k).size() == 1) { freeToDrop = selected.get(k); break; }
            }
            List<ParticipantId> pairToAdd = null;
            for (List<ParticipantId> unit : skipped) {
                if (unit.size() == 2) { pairToAdd = unit; break; }
            }
            if (freeToDrop != null && pairToAdd != null) {
                selected.remove(freeToDrop);
                selected.add(pairToAdd);
            }
        }
        return selected;
    }

    private Comparator<List<ParticipantId>> unitOrder(
            Map<ParticipantId, Integer> playCount, Map<ParticipantId, Integer> consecutivePlays) {
        // 出場優先度(前ほど出場・後ほど休憩)。固定ペアの2人は常に一緒に出入りするので
        // 代表(先頭)の値で判定してよい。
        // (1) 出場回数が少ない順 … 公平性を最優先(出場回数の差は最大1に保たれる)。
        // (2) 連続出場が少ない順 … 同じ出場回数なら、長く連続出場している人を後ろ=休憩に回す。
        //     直前に休んだ人は連続0で前=出場になるため、連続休みも同時に避けられる。
        return Comparator.comparingInt((List<ParticipantId> u) -> playCount.get(u.get(0)))
                .thenComparingInt(u -> consecutivePlays.get(u.get(0)));
    }

    /** 出場ユニットを Pair のリストにする。固定ペアはそのまま、自由参加者はシャッフルして2人ずつ。 */
    private List<Pair> buildPairs(List<List<ParticipantId>> selectedUnits) {
        List<Pair> pairs = new ArrayList<>();
        List<ParticipantId> frees = new ArrayList<>();
        for (List<ParticipantId> unit : selectedUnits) {
            if (unit.size() == 2) {
                pairs.add(new Pair(unit.get(0), unit.get(1)));
            } else {
                frees.add(unit.get(0));
            }
        }
        fisherYatesShuffle(frees);
        for (int k = 0; k + 1 < frees.size(); k += 2) {
            pairs.add(new Pair(frees.get(k), frees.get(k + 1)));
        }
        return pairs;
    }

    /** 出場ユニットに含まれない(=休む)メンバーの集合。 */
    private Set<ParticipantId> restMembers(
            List<List<ParticipantId>> units, List<List<ParticipantId>> selectedUnits) {
        Set<ParticipantId> rest = new HashSet<>();
        for (List<ParticipantId> unit : units) rest.addAll(unit);
        for (List<ParticipantId> unit : selectedUnits) unit.forEach(rest::remove);
        return rest;
    }

    /**
     * 休憩ユニットの組が直近セットと同じにならないよう、必要なら出場ユニットと入れ替える。
     * 入れ替えは公平性・連続休み回避を壊さない範囲だけで行う: 休む予定 r と出場予定 s を、
     * 同じサイズ(人数保存)・同じ出場回数・s が直前セットで休んでいない、ときにだけ交換する。
     * ユニット単位で扱うので固定ペアは崩れない。
     */
    private List<List<ParticipantId>> avoidRepeatedRest(
            List<List<ParticipantId>> units, List<List<ParticipantId>> selectedUnits,
            Map<ParticipantId, Integer> playCount, Map<ParticipantId, Integer> lastPlayedSet,
            int setNumber, Deque<Set<ParticipantId>> recentRest) {
        Set<ParticipantId> rest = restMembers(units, selectedUnits);
        if (!recentRest.contains(rest)) return selectedUnits;

        List<List<ParticipantId>> restingUnits = new ArrayList<>();
        for (List<ParticipantId> unit : units) {
            if (!selectedUnits.contains(unit)) restingUnits.add(unit);
        }
        fisherYatesShuffle(restingUnits);
        List<List<ParticipantId>> playingUnits = new ArrayList<>(selectedUnits);
        fisherYatesShuffle(playingUnits);

        for (List<ParticipantId> r : restingUnits) {
            for (List<ParticipantId> s : playingUnits) {
                if (r.size() != s.size()) continue; // 人数を保つため同サイズのみ
                boolean sameCount = playCount.get(r.get(0)).equals(playCount.get(s.get(0)));
                // s は直前セットに出ている(lastPlayed==setNumber-1)ときだけ休ませてよい。
                boolean sNotConsecutive = lastPlayedSet.get(s.get(0)) >= setNumber - 1;
                if (sameCount && sNotConsecutive) {
                    Set<ParticipantId> candidateRest = new HashSet<>(rest);
                    r.forEach(candidateRest::remove);
                    candidateRest.addAll(s);
                    if (!recentRest.contains(candidateRest)) {
                        List<List<ParticipantId>> result = new ArrayList<>(selectedUnits);
                        result.remove(s);
                        result.add(r);
                        return result;
                    }
                }
            }
        }
        return selectedUnits;
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

    private void fisherYatesShuffle(List<?> list) {
        for (int i = list.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            Collections.swap(list, i, j);
        }
    }
}

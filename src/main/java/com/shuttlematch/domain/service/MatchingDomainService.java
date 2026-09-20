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
 *   <li>「同じ顔ぶれで同じコートに入る」重複を避ける: 同コート共起の履歴(敵味方の区別なし)を
 *       蓄積し、公平性を保った選抜候補とコート割りを共起の少ない組み合わせへ最適化する。
 *       さらにスケジュール全体を複数回生成し、(最大共起, 未共起ペア数, ばらつき) が
 *       最良のものを採用する。</li>
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
    public static final int DEFAULT_SET_COUNT = 20;

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

        List<Match> matches = bestOfRestarts(
                pool, List.of(), isLargeSearch(courtCount) ? 2 : SCHEDULE_RESTARTS, () -> {
            // 出場回数・最終出場セット・連続出場は全員 0 スタート(全員同条件)。
            Map<ParticipantId, Integer> playCount = new HashMap<>();
            pool.forEach(p -> playCount.put(p, 0));
            Map<ParticipantId, Integer> lastPlayedSet = new HashMap<>();
            pool.forEach(p -> lastPlayedSet.put(p, 0));
            // 連続出場数(何セット連続でコートに入っているか)。休むと0にリセット。
            // 身体ケアのため、公平性を保ちつつ長い連続出場を避けるのに使う。
            Map<ParticipantId, Integer> consecutivePlays = new HashMap<>();
            pool.forEach(p -> consecutivePlays.put(p, 0));
            // 同コート共起の履歴(空スタート)。同じ相手との繰り返しを避けるのに使う。
            // 敵味方は区別しない(現場ではコート内でペアを組み直して遊ぶことが多く、
            // 体験として意味があるのは「同じコートに入った顔ぶれ」だから)。
            return buildSets(
                    pool, playCount, lastPlayedSet, consecutivePlays, new HashMap<>(),
                    courtCount, setCount, 1, 1, fixedPairs, List.of());
        });
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

        int startSetNumber = existing.setCount() + 1;
        int startMatchNumber = maxMatchNumber(existing.matches()) + 1;

        List<Match> added = bestOfRestarts(
                pool, existing.matches(), isLargeSearch(courtCount) ? 2 : SCHEDULE_RESTARTS, () -> {
            // 既存の出場実績・連続出場・共起履歴を引き継ぐ(公平性と偏り回避が
            // 境界をまたいで効くように)。新規参加者は最大回数にシードして通常輪番に乗せる
            // (優先出場も、遅刻分の埋め合わせ出場もさせない)。
            Map<ParticipantId, Map<ParticipantId, Integer>> coCount = new HashMap<>();
            seedPairHistory(existing.matches(), coCount);
            return buildSets(
                    pool,
                    seededPlayCounts(pool, existing.matches()),
                    seededLastPlayedSet(pool, existing.matches(), existing.setCount()),
                    seededConsecutivePlays(pool, existing.matches(), existing.setCount()),
                    coCount, courtCount, additionalSetCount,
                    startSetNumber, startMatchNumber, fixedPairs,
                    restGroupsOf(pool, existing.matches()));
        });

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
     *   <li>新規(途中参加)は既存の最大回数にシードして、そのまま通常の輪番に乗せる。
     *       優先出場もさせないし、遅刻分を埋め合わせる連続出場もさせない(参加費で調整する前提)。</li>
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

        List<Match> future = bestOfRestarts(
                pool, committed, isLargeSearch(effectiveCourtCount) ? 2 : SCHEDULE_RESTARTS, () -> {
            // 開始済み試合の出場実績・共起履歴を引き継ぐ(再編成後も相手が偏らないように)。
            Map<ParticipantId, Map<ParticipantId, Integer>> coCount = new HashMap<>();
            seedPairHistory(committed, coCount);
            return buildSets(
                    pool,
                    seededPlayCounts(pool, committed),
                    seededLastPlayedSet(pool, committed, maxStartedSet),
                    seededConsecutivePlays(pool, committed, maxStartedSet),
                    coCount, effectiveCourtCount, futureSetCount,
                    maxStartedSet + 1, maxMatchNumber(committed) + 1, fixedPairs,
                    restGroupsOf(pool, committed));
        });

        List<Match> all = new ArrayList<>(committed);
        all.addAll(future);
        return new MatchSchedule(existing.roomId(), all);
    }

    /** スケジュール全体の生成をやり直す回数(最良の1つを採用する)。 */
    private static final int SCHEDULE_RESTARTS = 4;

    /**
     * コート数が多いほど1回の生成が重くなる(局所探索の空間が広がる)ため、
     * 5コート以上では探索量を半分に落として実行時間を抑える。
     * 大人数×多コートは組み合わせの自由度が高く、探索量を絞っても品質はほぼ落ちない。
     */
    private boolean isLargeSearch(int courtCount) {
        return courtCount >= 5;
    }

    /**
     * スケジュール(の追加分)の生成を {@link #SCHEDULE_RESTARTS} 回試し、確定分と合わせた
     * 全体の共起分布が最も良いものを採用する。1セットずつの貪欲な最適化は局所的には最良でも
     * 序盤の引きに全体の仕上がりが左右されるため、全体を数回作って結果で選ぶ。
     * 良さは (1) 最大共起回数が小さい (2) 一度も同コートにならないペアが少ない
     * (3) 共起回数のばらつきが小さい、の辞書式で比較する。
     */
    private List<Match> bestOfRestarts(
            List<ParticipantId> pool, List<Match> committed, int restarts,
            java.util.function.Supplier<List<Match>> attempt) {
        List<Match> best = null;
        long[] bestQuality = null;
        for (int r = 0; r < restarts; r++) {
            List<Match> cand = attempt.get();
            long[] quality = scheduleQuality(pool, committed, cand);
            if (best == null || java.util.Arrays.compare(quality, bestQuality) < 0) {
                best = cand;
                bestQuality = quality;
            }
        }
        return best;
    }

    /** 確定分+候補分を合わせた共起分布の質: {最大共起, 未共起ペア数, 共起の2乗和}。小さいほど良い。 */
    private long[] scheduleQuality(
            List<ParticipantId> pool, List<Match> committed, List<Match> candidate) {
        Map<ParticipantId, Map<ParticipantId, Integer>> coCount = new HashMap<>();
        seedPairHistory(committed, coCount);
        seedPairHistory(candidate, coCount);
        long maxCo = 0, neverMet = 0, sumSquares = 0;
        for (int i = 0; i < pool.size(); i++) {
            for (int j = i + 1; j < pool.size(); j++) {
                int c = pairGet(coCount, pool.get(i), pool.get(j));
                maxCo = Math.max(maxCo, c);
                if (c == 0) neverMet++;
                sumSquares += (long) c * c;
            }
        }
        return new long[] {maxCo, neverMet, sumSquares};
    }

    /**
     * 参照試合から出場回数を集計する。プールに居るが未出場の参加者(途中参加など)は
     * 「既に出ている人の最大回数」にシードして、通常の輪番へそのまま乗せる。
     * <p>
     * 最小回数にシードすると、途中参加者は出場回数が他に追いつくまで毎セット選抜先頭に
     * 立ち続け、残りセットが少ないと「一度も休めないまま終了」になる(2コート・終盤参加で
     * 実際に発生した)。遅刻分の埋め合わせ出場はさせず、参加時点で「帳尻が合っている人」
     * として扱う。埋め合わせない結果、残りセットでの出場はベテランよりやや少なめになる。
     */
    private Map<ParticipantId, Integer> seededPlayCounts(
            List<ParticipantId> pool, List<Match> sourceMatches) {
        Map<ParticipantId, Integer> appeared = new HashMap<>();
        for (Match m : sourceMatches) {
            for (ParticipantId p : participantsOf(m)) {
                appeared.merge(p, 1, Integer::sum);
            }
        }
        int lateComerSeed = pool.stream()
                .filter(appeared::containsKey)
                .mapToInt(appeared::get)
                .max()
                .orElse(0);
        Map<ParticipantId, Integer> playCount = new HashMap<>();
        for (ParticipantId p : pool) {
            playCount.put(p, appeared.getOrDefault(p, lateComerSeed));
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
            Map<ParticipantId, Map<ParticipantId, Integer>> coCount,
            int courtCount, int setCount, int startSetNumber, int startMatchNumber,
            List<Pair> fixedPairs, List<Set<ParticipantId>> seedRecentRest) {
        int required = PLAYERS_PER_MATCH * courtCount;
        List<List<ParticipantId>> units = buildUnits(pool, fixedPairs);
        boolean hasRest = pool.size() > required;
        // 休みが出場と同数以上(2コートで16人以上など)だと、連続休み禁止を厳密に守ると
        // 出場グループが2つに固定されて全く混ざらなくなる。この場合だけ連続休みを許容して混ぜる。
        boolean mixingMode = pool.size() >= 2 * required;

        // 連続休みセット数。lastPlayedSet(最後に出場したセット)から復元する。
        // セット追加・再編成でも、直前の確定セットからの連続休みを引き継げる。
        Map<ParticipantId, Integer> consecutiveRests = new HashMap<>();
        for (ParticipantId p : pool) {
            consecutiveRests.put(p, Math.max(0, (startSetNumber - 1) - lastPlayedSet.get(p)));
        }

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
            // 出場者の選抜。公平性(出場回数)は保ったまま、境界のタイブレークを
            // 「これまで同じコートに一緒になっていない人同士」に寄せて、共起の偏りを減らす。
            List<List<ParticipantId>> selectedUnits;
            if (mixingMode) {
                // 連続休みを許容して2グループ固定を崩し、混ざり合いを優先する。
                selectedUnits = pickUnitsMixing(
                        units, playCount, consecutiveRests, required, courtCount, coCount);
            } else {
                selectedUnits = pickUnitsDiverse(
                        units, playCount, lastPlayedSet, consecutivePlays, required,
                        courtCount, coCount);
                if (hasRest) {
                    selectedUnits = avoidRepeatedRest(
                            units, selectedUnits, playCount, lastPlayedSet, setNumber, recentRest);
                    Set<ParticipantId> rest = restMembers(units, selectedUnits);
                    recentRest.addLast(rest);
                    while (recentRest.size() > restWindow) recentRest.removeFirst();
                }
            }

            // コート割り(=同じコートの4人の組)を、同じコートに一緒になった履歴が
            // 少ない組み合わせに寄せる。「同じメンツで同じコートに入る」重複を減らすのが狙い。
            List<Pair> pairs = bestArrangement(
                    selectedUnits, courtCount, coCount,
                    isLargeSearch(courtCount) ? ARRANGEMENT_STARTS / 2 : ARRANGEMENT_STARTS,
                    !mixingMode);
            for (int court = 1; court <= courtCount; court++) {
                Pair pairA = pairs.get((court - 1) * 2);
                Pair pairB = pairs.get((court - 1) * 2 + 1);
                matches.add(Match.of(MatchNumber.of(matchNumber++), setNumber, court, pairA, pairB));
                recordArrangement(coCount, pairA, pairB);
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
            // 休んだ人は連続出場をリセットし、連続休みを+1。出場した人は連続休みを0に。
            for (ParticipantId p : pool) {
                if (!playing.contains(p)) {
                    consecutivePlays.put(p, 0);
                    consecutiveRests.merge(p, 1, Integer::sum);
                } else {
                    consecutiveRests.put(p, 0);
                }
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

    /** 指定した優先度(comparator)でユニットを選ぶ。前ほど出場・後ほど休憩。 */
    private List<List<ParticipantId>> pickUnits(
            List<List<ParticipantId>> units, int required,
            Comparator<List<ParticipantId>> order) {
        List<List<ParticipantId>> candidates = new ArrayList<>(units);
        fisherYatesShuffle(candidates);
        candidates.sort(order);

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

    /** 出場者選抜の候補生成の試行回数(公平性は保ったまま、共起の少ない顔ぶれを選ぶ)。 */
    private static final int SELECTION_ATTEMPTS = 60;
    /** 選抜候補として評価する「異なる顔ぶれ」の最大数(1候補ごとにコート割りまで試すため)。 */
    private static final int MAX_SELECTION_CANDIDATES = 16;
    /** 味方ペア分け・コート割りの局所探索(山登り)の初期解の数(最終決定用)。 */
    private static final int ARRANGEMENT_STARTS = 24;
    /** 選抜候補の比較評価に使う局所探索の初期解の数(候補数が多いので軽めにする)。 */
    private static final int CANDIDATE_ARRANGEMENT_STARTS = 6;
    /** 連続で休んでよい最大セット数(混ぜモード時)。2 = 「1回まで連続休みを許容」。 */
    private static final int MAX_CONSECUTIVE_REST = 2;
    /** 連続休みが上限を超える選抜を実質禁止するための大きなペナルティ(共起コストより常に重い)。 */
    private static final long REST_CAP_PENALTY = 1L << 50;

    /**
     * 2人を「もう一度」同じコートに入れることの重み。
     * <ul>
     *   <li>未共起(c=0)は負(=積極的に同席させて初顔合わせを作る)。</li>
     *   <li>凸(convex=true): 4^c - 1。線形和だと「5回目の再会」も「1回目の顔合わせ」も同じ
     *       1点で、少数のペアに重複が集中しても合計が同じなら選ばれてしまう。指数重みにすると
     *       回数の多いペアの再共起が支配的なコストになり、実質「最大共起回数の最小化」
     *       (全体が均等に混ざる)へ寄る。通常モード(休み&lt;出場)向け。</li>
     *   <li>線形(convex=false): c。混ぜモード(休み≧出場、16人2コートなど)では共起回数の
     *       上限より「未共起ペアを減らす」方が体験に効くため、最大値の圧縮に偏らない
     *       線形重みでカバレッジを優先する。</li>
     * </ul>
     */
    private long pairCost(int count, boolean convex) {
        if (count == 0) return -FIRST_MEET_BONUS;
        return convex ? (1L << Math.min(2 * count, 40)) - 1 : count;
    }

    /** まだ同コートになったことのない2人を同時に選ぶことへのボーナス(初顔合わせの機会を作る)。 */
    private static final long FIRST_MEET_BONUS = 2;

    /**
     * 休みが出場と同数以上になる構成(2コートで16人以上など)向けの選抜。
     * 「連続休み禁止」を厳密に守ると出場グループが2つに固定され全く混ざらなくなるため、
     * 連続休みを {@link #MAX_CONSECUTIVE_REST} まで許容し、その自由度を使って
     * 「同じコートに一緒になっていない顔ぶれ」を優先する。公平性(出場回数)は保つ。
     */
    private List<List<ParticipantId>> pickUnitsMixing(
            List<List<ParticipantId>> units, Map<ParticipantId, Integer> playCount,
            Map<ParticipantId, Integer> consecutiveRests, int required, int courtCount,
            Map<ParticipantId, Map<ParticipantId, Integer>> coCount) {
        // 出場回数のみを厳守(公平性)。連続出場/休みは順位に入れず、共起最小化に自由を与える。
        Comparator<List<ParticipantId>> order =
                Comparator.comparingInt(u -> playCount.get(u.get(0)));
        Set<ParticipantId> all = new HashSet<>();
        for (List<ParticipantId> u : units) all.addAll(u);

        List<List<ParticipantId>> best = null;
        long bestPrimary = Long.MAX_VALUE;
        long bestSecondary = Long.MAX_VALUE;
        for (Map.Entry<Set<ParticipantId>, List<List<ParticipantId>>> e :
                distinctSelections(units, required, order,
                        isLargeSearch(courtCount) ? MAX_SELECTION_CANDIDATES / 2 : MAX_SELECTION_CANDIDATES)
                .entrySet()) {
            List<List<ParticipantId>> sel = e.getValue();
            // 総当たり共起コスト + 連続休み上限超過への大ペナルティ(主)。
            long primary = selectionCost(sel, coCount, false);
            for (ParticipantId p : all) {
                if (!e.getKey().contains(p)
                        && consecutiveRests.getOrDefault(p, 0) >= MAX_CONSECUTIVE_REST) {
                    primary += REST_CAP_PENALTY; // この人をこれ以上続けて休ませない
                }
            }
            if (primary > bestPrimary) continue;
            long secondary = arrangedCost(sel, courtCount, coCount, false);
            if (primary < bestPrimary || secondary < bestSecondary) {
                bestPrimary = primary;
                bestSecondary = secondary;
                best = sel;
            }
        }
        return best;
    }

    /**
     * 出場者を選ぶ。{@link #pickUnits} を複数回試し(タイブレークのランダム性で毎回少し変わる)、
     * 公平性は同じまま「選ばれた人同士がこれまで同じコートに一緒になっていない」度合いが
     * 最も高い顔ぶれを選ぶ(主)。総当たりコストが同じ候補は「実際にコート割りしたときの
     * コスト」が小さい方を採る(従)。総当たりを主にするのは、今は別コートへ分けられる
     * 2人でも、同じ顔ぶれの共選抜を繰り返せばいずれ同コートを強いられるため
     * (コート割り後コストだけで選ぶと長期的にはかえって偏る)。
     */
    private List<List<ParticipantId>> pickUnitsDiverse(
            List<List<ParticipantId>> units, Map<ParticipantId, Integer> playCount,
            Map<ParticipantId, Integer> lastPlayedSet, Map<ParticipantId, Integer> consecutivePlays,
            int required, int courtCount,
            Map<ParticipantId, Map<ParticipantId, Integer>> coCount) {
        List<List<ParticipantId>> best = null;
        long bestPrimary = Long.MAX_VALUE;
        long bestSecondary = Long.MAX_VALUE;
        for (List<List<ParticipantId>> sel :
                distinctSelections(units, required, unitOrder(playCount, consecutivePlays),
                        isLargeSearch(courtCount) ? MAX_SELECTION_CANDIDATES / 2 : MAX_SELECTION_CANDIDATES)
                .values()) {
            long primary = selectionCost(sel, coCount, true);
            if (primary > bestPrimary) continue;
            long secondary = arrangedCost(sel, courtCount, coCount, true);
            if (primary < bestPrimary || secondary < bestSecondary) {
                bestPrimary = primary;
                bestSecondary = secondary;
                best = sel;
            }
        }
        return best;
    }

    /** 選ばれた出場者全員について、これまでの同コート共起の重みを総当たりで足したコスト。 */
    private long selectionCost(
            List<List<ParticipantId>> selectedUnits,
            Map<ParticipantId, Map<ParticipantId, Integer>> coCount, boolean convex) {
        List<ParticipantId> players = new ArrayList<>();
        for (List<ParticipantId> unit : selectedUnits) players.addAll(unit);
        long cost = 0;
        for (int i = 0; i < players.size(); i++) {
            for (int j = i + 1; j < players.size(); j++) {
                cost += pairCost(pairGet(coCount, players.get(i), players.get(j)), convex);
            }
        }
        return cost;
    }

    /**
     * {@link #pickUnits} を複数回試し、顔ぶれ(出場者の集合)が異なる選抜候補を集める。
     * タイブレークのランダム性で毎回少し変わるが同じ顔ぶれになることも多いので、
     * 重複を除いて {@link #MAX_SELECTION_CANDIDATES} 件まで集める。
     */
    private Map<Set<ParticipantId>, List<List<ParticipantId>>> distinctSelections(
            List<List<ParticipantId>> units, int required,
            Comparator<List<ParticipantId>> order, int maxCandidates) {
        Map<Set<ParticipantId>, List<List<ParticipantId>>> candidates = new HashMap<>();
        for (int t = 0; t < SELECTION_ATTEMPTS && candidates.size() < maxCandidates; t++) {
            List<List<ParticipantId>> sel = pickUnits(units, required, order);
            Set<ParticipantId> key = new HashSet<>();
            for (List<ParticipantId> u : sel) key.addAll(u);
            candidates.putIfAbsent(key, sel);
        }
        return candidates;
    }

    /** 選抜候補を「実際に最良のコート割りをしたときのコスト」で評価する(軽めの局所探索)。 */
    private long arrangedCost(
            List<List<ParticipantId>> selectedUnits, int courtCount,
            Map<ParticipantId, Map<ParticipantId, Integer>> coCount, boolean convex) {
        List<Pair> arranged = bestArrangement(
                selectedUnits, courtCount, coCount,
                isLargeSearch(courtCount) ? CANDIDATE_ARRANGEMENT_STARTS / 2 : CANDIDATE_ARRANGEMENT_STARTS,
                convex);
        return arrangementCost(arranged, courtCount, coCount, convex);
    }

    /**
     * 出場ユニットから、ペア分けとコート割り(=同じコートの4人の組)を決める。
     * ランダムな初期解から山登り法(選手同士・ペア同士のスワップで改善が止まるまで)で
     * 局所最適に降ろし、それを複数の初期解で繰り返して最良を採る。単純なランダム試行より
     * 探索空間を確実にカバーでき、「これまで同じコートに一緒になった重み」を最小化する。
     */
    private List<Pair> bestArrangement(
            List<List<ParticipantId>> selectedUnits, int courtCount,
            Map<ParticipantId, Map<ParticipantId, Integer>> coCount, int starts, boolean convex) {
        // 固定ペアのメンバーはペア分けスワップの対象外(常に2人で1つの Pair)。
        Set<ParticipantId> frees = new HashSet<>();
        for (List<ParticipantId> unit : selectedUnits) {
            if (unit.size() == 1) frees.add(unit.get(0));
        }
        List<Pair> best = null;
        long bestCost = Long.MAX_VALUE;
        for (int t = 0; t < starts; t++) {
            List<Pair> cand = buildPairs(selectedUnits); // 固定ペアはそのまま、自由参加者は2人ずつ
            fisherYatesShuffle(cand); // コート割り・pairA/pairB をランダム化
            long cost = localImprove(cand, courtCount, frees, coCount, convex);
            if (cost < bestCost) {
                bestCost = cost;
                best = cand;
            }
        }
        return best;
    }

    /**
     * 山登り法。改善がある限り (1) ペア同士のコート入れ替え、(2) 別コートの自由参加者
     * 同士の入れ替え、を繰り返す。cand を直接書き換え、局所最適のコストを返す。
     */
    private long localImprove(
            List<Pair> cand, int courtCount, Set<ParticipantId> frees,
            Map<ParticipantId, Map<ParticipantId, Integer>> coCount, boolean convex) {
        long cost = arrangementCost(cand, courtCount, coCount, convex);
        boolean improved = true;
        while (improved) {
            improved = false;
            // (1) ペアを別コートへ入れ替える(コートの顔ぶれが変わる)。
            for (int i = 0; i < cand.size(); i++) {
                for (int j = i + 1; j < cand.size(); j++) {
                    if (i / 2 == j / 2) continue; // 同じコート内の入替はコストに影響しない
                    Collections.swap(cand, i, j);
                    long c = arrangementCost(cand, courtCount, coCount, convex);
                    if (c < cost) {
                        cost = c;
                        improved = true;
                    } else {
                        Collections.swap(cand, i, j);
                    }
                }
            }
            // (2) 別コートの自由参加者同士を入れ替える(コートの顔ぶれが変わる)。
            // Pair は生成時に2人を昇順へ正規化するため、スロット位置ではなく
            // 参加者IDで入れ替える(IDベースなら巻き戻しが正確に元へ戻る)。
            for (int i = 0; i < cand.size(); i++) {
                for (int j = i + 1; j < cand.size(); j++) {
                    if (i / 2 == j / 2) continue; // 同じコート内の入替はコストに影響しない
                    for (int si = 0; si < 2; si++) {
                        for (int sj = 0; sj < 2; sj++) {
                            ParticipantId a = playerAt(cand.get(i), si);
                            ParticipantId b = playerAt(cand.get(j), sj);
                            if (!frees.contains(a) || !frees.contains(b)) continue;
                            swapPlayers(cand, i, a, j, b);
                            long c = arrangementCost(cand, courtCount, coCount, convex);
                            if (c < cost) {
                                cost = c;
                                improved = true;
                            } else {
                                swapPlayers(cand, i, b, j, a); // 元に戻す
                            }
                        }
                    }
                }
            }
        }
        return cost;
    }

    private ParticipantId playerAt(Pair pair, int slot) {
        return slot == 0 ? pair.player1() : pair.player2();
    }

    /** ペア i の選手 a と、ペア j の選手 b を入れ替える。 */
    private void swapPlayers(List<Pair> pairs, int i, ParticipantId a, int j, ParticipantId b) {
        pairs.set(i, replaced(pairs.get(i), a, b));
        pairs.set(j, replaced(pairs.get(j), b, a));
    }

    /** ペアの from を to に差し替えた新しいペア。 */
    private Pair replaced(Pair pair, ParticipantId from, ParticipantId to) {
        return pair.player1().equals(from)
                ? new Pair(to, pair.player2())
                : new Pair(pair.player1(), to);
    }

    /**
     * 組み合わせのコスト。各コートの4人について、全6ペアの「これまでの同コート共起回数」の
     * 重み({@link #pairCost})を足し合わせる。敵味方は区別しない(現場ではコート内で
     * ペアを組み直して遊ぶことが多いため)。合計が小さいほど「新鮮な顔合わせ」になる。
     */
    private long arrangementCost(
            List<Pair> pairs, int courtCount,
            Map<ParticipantId, Map<ParticipantId, Integer>> coCount, boolean convex) {
        long cost = 0;
        for (int court = 0; court < courtCount; court++) {
            Pair a = pairs.get(court * 2);
            Pair b = pairs.get(court * 2 + 1);
            ParticipantId[] four = {a.player1(), a.player2(), b.player1(), b.player2()};
            for (int i = 0; i < 4; i++) {
                for (int j = i + 1; j < 4; j++) {
                    cost += pairCost(pairGet(coCount, four[i], four[j]), convex);
                }
            }
        }
        return cost;
    }

    /** 決めた組み合わせを履歴に反映する(同じコートの4人の全ペアの共起を +1)。 */
    private void recordArrangement(
            Map<ParticipantId, Map<ParticipantId, Integer>> coCount, Pair pairA, Pair pairB) {
        List<ParticipantId> four = List.of(
                pairA.player1(), pairA.player2(), pairB.player1(), pairB.player2());
        for (int i = 0; i < 4; i++) {
            for (int j = i + 1; j < 4; j++) {
                pairInc(coCount, four.get(i), four.get(j));
            }
        }
    }

    /** 既存の試合から同コート共起の履歴を復元する(セット追加・再編成で継続させるため)。 */
    private void seedPairHistory(
            List<Match> matches, Map<ParticipantId, Map<ParticipantId, Integer>> coCount) {
        for (Match m : matches) {
            recordArrangement(coCount, m.pairA(), m.pairB());
        }
    }

    /** 無向の回数マップから a-b の回数を取り出す。 */
    private int pairGet(
            Map<ParticipantId, Map<ParticipantId, Integer>> counts, ParticipantId a, ParticipantId b) {
        Map<ParticipantId, Integer> inner = counts.get(a);
        return inner == null ? 0 : inner.getOrDefault(b, 0);
    }

    /** 無向の回数マップで a-b を両方向に +1 する。 */
    private void pairInc(
            Map<ParticipantId, Map<ParticipantId, Integer>> counts, ParticipantId a, ParticipantId b) {
        counts.computeIfAbsent(a, k -> new HashMap<>()).merge(b, 1, Integer::sum);
        counts.computeIfAbsent(b, k -> new HashMap<>()).merge(a, 1, Integer::sum);
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

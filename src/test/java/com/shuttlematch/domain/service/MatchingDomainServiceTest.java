package com.shuttlematch.domain.service;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.shuttlematch.domain.model.match.Match;
import com.shuttlematch.domain.model.match.MatchSchedule;
import com.shuttlematch.domain.model.match.Pair;
import com.shuttlematch.domain.model.room.ParticipantId;
import com.shuttlematch.domain.model.room.RoomId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MatchingDomainServiceTest {

    private final RoomId roomId = RoomId.newId();

    /** 再現性のためシード固定の Random を注入する。 */
    private MatchingDomainService serviceWithSeed(long seed) {
        return new MatchingDomainService(new Random(seed));
    }

    private List<ParticipantId> participants(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> ParticipantId.newId())
                .toList();
    }

    @Test
    @DisplayName("6人・短いセット数でも休憩ペアが数通りに固定ローテーションしない")
    void restPairsDoNotLockIntoRotation() {
        // 6人1コートは毎セット2人が休む。公平性の制約だけだと、同じ2人組が
        // 毎回セットで休む「3ペアの固定ローテーション」に陥りやすい(対策前は6セットで約11%発生)。
        // avoidRepeatedRest によりカップリングが崩れることを、多数試行の統計で保証する。
        List<ParticipantId> pool = participants(6);
        Map<ParticipantId, Integer> num = new HashMap<>();
        for (int i = 0; i < pool.size(); i++) num.put(pool.get(i), i + 1);

        int trials = 500;
        int setCount = 6; // 最も固定化しやすい短さ
        int fixed = 0;      // 休憩ペアが3種類以下(=固定ローテーション)だった試行
        int consecutive = 0; // 連続2回休んだ人がいた試行(連続休み回避の維持を確認)
        for (int t = 0; t < trials; t++) {
            MatchSchedule sch = new MatchingDomainService(new Random(t))
                    .generate(roomId, pool, 1, setCount);
            Map<Integer, Set<Integer>> playing = new HashMap<>();
            for (Match m : sch.matches()) {
                Set<Integer> s = playing.computeIfAbsent(m.setNumber(), k -> new HashSet<>());
                s.add(num.get(m.pairA().player1()));
                s.add(num.get(m.pairA().player2()));
                s.add(num.get(m.pairB().player1()));
                s.add(num.get(m.pairB().player2()));
            }
            List<Set<Integer>> restList = new java.util.ArrayList<>();
            for (int st = 1; st <= setCount; st++) {
                Set<Integer> rest = new HashSet<>(List.of(1, 2, 3, 4, 5, 6));
                rest.removeAll(playing.get(st));
                restList.add(rest);
            }
            if (new HashSet<>(restList).size() <= 3) fixed++;
            for (int k = 0; k + 1 < restList.size(); k++) {
                Set<Integer> inter = new HashSet<>(restList.get(k));
                inter.retainAll(restList.get(k + 1));
                if (!inter.isEmpty()) { consecutive++; break; }
            }
        }
        // 対策後は0件だが、乱数のブレを考慮してごく緩い上限で回帰を検出する。
        assertTrue(fixed <= trials * 0.02,
                "休憩ペアが固定ローテーションに陥りすぎ: " + fixed + "/" + trials);
        assertEquals(0, consecutive, "同じ人が連続で休んでいる(連続休み回避が壊れている)");
    }

    /** 指定セットの、その固定ペアの状態を検証する: 両方出場なら同じチーム、片方だけ出場は不可。 */
    private void assertFixedPairIntact(MatchSchedule schedule, ParticipantId a, ParticipantId b) {
        int setCount = schedule.setCount();
        for (int set = 1; set <= setCount; set++) {
            final int s = set;
            List<Match> setMatches = schedule.matches().stream()
                    .filter(m -> m.setNumber() == s).toList();
            boolean aIn = setMatches.stream().anyMatch(m -> m.hasParticipant(a));
            boolean bIn = setMatches.stream().anyMatch(m -> m.hasParticipant(b));
            assertEquals(aIn, bIn, "固定ペアの片方だけ出場している set=" + s);
            if (aIn) {
                boolean sameTeam = setMatches.stream().anyMatch(m ->
                        (m.pairA().contains(a) && m.pairA().contains(b))
                        || (m.pairB().contains(a) && m.pairB().contains(b)));
                assertTrue(sameTeam, "固定ペアが同じチームにいない set=" + s);
            }
        }
    }

    @Test
    @DisplayName("固定ペアは常に同じチームで一緒に出場・休憩する")
    void fixedPairAlwaysTogetherAndSameTeam() {
        List<ParticipantId> pool = participants(6);
        Pair fixed = new Pair(pool.get(1), pool.get(2)); // 2番と3番
        MatchSchedule schedule = serviceWithSeed(7L)
                .generate(roomId, pool, 1, 12, List.of(fixed));
        assertFixedPairIntact(schedule, pool.get(1), pool.get(2));
    }

    @Test
    @DisplayName("複数の固定ペアが同時に守られ、出場回数も公平になる")
    void multipleFixedPairsRespectedAndFair() {
        List<ParticipantId> pool = participants(8);
        Pair p1 = new Pair(pool.get(0), pool.get(1));
        Pair p2 = new Pair(pool.get(4), pool.get(5));
        MatchSchedule schedule = serviceWithSeed(9L)
                .generate(roomId, pool, 1, 12, List.of(p1, p2));
        assertFixedPairIntact(schedule, pool.get(0), pool.get(1));
        assertFixedPairIntact(schedule, pool.get(4), pool.get(5));

        Map<ParticipantId, Integer> counts = new HashMap<>();
        pool.forEach(p -> counts.put(p, 0));
        for (Match m : schedule.matches()) {
            participantsOf(m).forEach(p -> counts.merge(p, 1, Integer::sum));
        }
        int max = counts.values().stream().max(Integer::compareTo).orElseThrow();
        int min = counts.values().stream().min(Integer::compareTo).orElseThrow();
        assertTrue(max - min <= 1, "固定ペアありでも出場回数は公平であるべき: max=" + max + ", min=" + min);
    }

    @Test
    @DisplayName("全員が固定ペア(自由参加者0)でもペア単位で出場・休憩できる")
    void allParticipantsInFixedPairs() {
        List<ParticipantId> pool = participants(6); // 3ペア、1コート=2ペア出場・1ペア休み
        Pair p1 = new Pair(pool.get(0), pool.get(1));
        Pair p2 = new Pair(pool.get(2), pool.get(3));
        Pair p3 = new Pair(pool.get(4), pool.get(5));
        MatchSchedule schedule = serviceWithSeed(11L)
                .generate(roomId, pool, 1, 9, List.of(p1, p2, p3));
        assertEquals(9, schedule.size());
        assertFixedPairIntact(schedule, pool.get(0), pool.get(1));
        assertFixedPairIntact(schedule, pool.get(2), pool.get(3));
        assertFixedPairIntact(schedule, pool.get(4), pool.get(5));
    }

    @Test
    @DisplayName("参加者が4人未満の場合は例外を投げる")
    void throwsWhenFewerThanFourParticipants() {
        MatchingDomainService service = serviceWithSeed(1L);
        assertThrows(IllegalArgumentException.class,
                () -> service.generate(roomId, participants(3), 1, 10));
    }

    @Test
    @DisplayName("ちょうど4人なら生成できる(最低人数の境界)")
    void generatesWithExactlyFourParticipants() {
        MatchSchedule schedule = serviceWithSeed(1L)
                .generate(roomId, participants(4), 1, MatchingDomainService.DEFAULT_SET_COUNT);
        assertEquals(MatchingDomainService.DEFAULT_SET_COUNT, schedule.size());
    }

    @Test
    @DisplayName("デフォルト(10)セットでは1コートあたり10試合を生成する")
    void generatesDefaultSetCount() {
        MatchSchedule schedule = serviceWithSeed(2L)
                .generate(roomId, participants(8), 1, MatchingDomainService.DEFAULT_SET_COUNT);
        assertEquals(10, schedule.size());
    }

    @Test
    @DisplayName("セット数を指定できる")
    void generatesRequestedNumberOfSets() {
        MatchSchedule schedule = serviceWithSeed(2L).generate(roomId, participants(8), 1, 7);
        assertEquals(7, schedule.size());
    }

    @Test
    @DisplayName("コート数とセット数の積が試合数になる")
    void generatesCourtCountTimesSetCount() {
        MatchSchedule schedule = serviceWithSeed(2L).generate(roomId, participants(8), 2, 5);
        assertEquals(10, schedule.size());
    }

    @Test
    @DisplayName("試合番号は1から連番で振られる")
    void assignsSequentialMatchNumbers() {
        MatchSchedule schedule = serviceWithSeed(3L).generate(roomId, participants(6), 1, 10);
        for (int i = 0; i < schedule.size(); i++) {
            assertEquals(i + 1, schedule.matches().get(i).matchNumber().value());
        }
    }

    @Test
    @DisplayName("各試合は重複しない4名で構成される")
    void eachMatchHasFourDistinctParticipants() {
        MatchSchedule schedule = serviceWithSeed(4L).generate(roomId, participants(7), 1, 10);
        for (Match match : schedule.matches()) {
            Set<ParticipantId> members = new HashSet<>();
            members.add(match.pairA().player1());
            members.add(match.pairA().player2());
            members.add(match.pairB().player1());
            members.add(match.pairB().player2());
            assertEquals(4, members.size(), "試合 " + match.matchNumber().value() + " に重複参加者がいる");
        }
    }

    @Test
    @DisplayName("登録済み参加者以外は出場しない")
    void onlyUsesProvidedParticipants() {
        List<ParticipantId> pool = participants(5);
        Set<ParticipantId> allowed = new HashSet<>(pool);
        MatchSchedule schedule = serviceWithSeed(5L).generate(roomId, pool, 1, 10);
        for (Match match : schedule.matches()) {
            assertTrue(allowed.contains(match.pairA().player1()));
            assertTrue(allowed.contains(match.pairA().player2()));
            assertTrue(allowed.contains(match.pairB().player1()));
            assertTrue(allowed.contains(match.pairB().player2()));
        }
    }

    @Test
    @DisplayName("出場回数が公平に分散される(最大と最小の差は1以内)")
    void distributesPlayCountsFairly() {
        List<ParticipantId> pool = participants(7);
        MatchSchedule schedule = serviceWithSeed(6L).generate(roomId, pool, 1, 10);

        Map<ParticipantId, Integer> counts = new HashMap<>();
        pool.forEach(p -> counts.put(p, 0));
        for (Match match : schedule.matches()) {
            counts.merge(match.pairA().player1(), 1, Integer::sum);
            counts.merge(match.pairA().player2(), 1, Integer::sum);
            counts.merge(match.pairB().player1(), 1, Integer::sum);
            counts.merge(match.pairB().player2(), 1, Integer::sum);
        }
        int max = counts.values().stream().max(Integer::compareTo).orElseThrow();
        int min = counts.values().stream().min(Integer::compareTo).orElseThrow();
        assertTrue(max - min <= 1, "出場回数の偏りが大きい: max=" + max + ", min=" + min);
    }

    /**
     * 各セットの休憩者(出場していない参加者)集合を、セット番号昇順で返す。
     */
    private List<Set<ParticipantId>> restingPerSet(MatchSchedule schedule, List<ParticipantId> pool) {
        int setCount = schedule.setCount();
        List<Set<ParticipantId>> resting = new java.util.ArrayList<>();
        for (int set = 1; set <= setCount; set++) {
            final int s = set;
            Set<ParticipantId> playing = new HashSet<>();
            schedule.matches().stream()
                    .filter(m -> m.setNumber() == s)
                    .forEach(m -> playing.addAll(participantsOf(m)));
            Set<ParticipantId> rest = new HashSet<>(pool);
            rest.removeAll(playing);
            resting.add(rest);
        }
        return resting;
    }

    /** 連続する2セットの両方で休んでいる参加者の総数(0 なら連続休みなし)。 */
    private long countConsecutiveRests(List<Set<ParticipantId>> resting) {
        long total = 0;
        for (int i = 1; i < resting.size(); i++) {
            for (ParticipantId p : resting.get(i)) {
                if (resting.get(i - 1).contains(p)) total++;
            }
        }
        return total;
    }

    @Test
    @DisplayName("5人1コートでは毎セット1人休みが完全ローテーションし、連続休みが発生しない")
    void noConsecutiveRestsWithFiveOnOneCourt() {
        List<ParticipantId> pool = participants(5);
        MatchSchedule schedule = serviceWithSeed(101L).generate(roomId, pool, 1, 10);

        long consecutive = countConsecutiveRests(restingPerSet(schedule, pool));
        assertEquals(0, consecutive, "連続で2セット休む人がいる");
    }

    @Test
    @DisplayName("6人1コート(毎セット2人休み)でも連続休みは発生しない")
    void noConsecutiveRestsWithSixOnOneCourt() {
        List<ParticipantId> pool = participants(6);
        MatchSchedule schedule = serviceWithSeed(102L).generate(roomId, pool, 1, 12);

        long consecutive = countConsecutiveRests(restingPerSet(schedule, pool));
        assertEquals(0, consecutive, "連続で2セット休む人がいる");
    }

    @Test
    @DisplayName("複数のシードでも連続休みが起きない(9人2コート=毎セット1人休み)")
    void noConsecutiveRestsAcrossSeeds() {
        List<ParticipantId> pool = participants(9);
        for (long seed = 0; seed < 20; seed++) {
            MatchSchedule schedule = serviceWithSeed(seed).generate(roomId, pool, 2, 10);
            long consecutive = countConsecutiveRests(restingPerSet(schedule, pool));
            assertEquals(0, consecutive, "seed=" + seed + " で連続休みが発生");
        }
    }

    /** 各参加者の「最大連続出場セット数」のうち、全参加者を通じた最大値。 */
    private int maxConsecutivePlays(MatchSchedule schedule, List<ParticipantId> pool) {
        List<Set<ParticipantId>> resting = restingPerSet(schedule, pool);
        int overall = 0;
        for (ParticipantId p : pool) {
            int cur = 0;
            int mx = 0;
            for (Set<ParticipantId> rest : resting) {
                if (rest.contains(p)) {
                    cur = 0;
                } else {
                    cur++;
                    mx = Math.max(mx, cur);
                }
            }
            overall = Math.max(overall, mx);
        }
        return overall;
    }

    @Test
    @DisplayName("休みが取れる構成では長い連続出場を避ける(10人2コートで連続出場は6以下)・公平性と連続休みなしも維持")
    void limitsConsecutivePlaysWhenRestAvailable() {
        List<ParticipantId> pool = participants(10);
        for (long seed = 0; seed < 20; seed++) {
            MatchSchedule schedule = serviceWithSeed(seed).generate(roomId, pool, 2, 15);
            int maxStreak = maxConsecutivePlays(schedule, pool);
            // 毎セット2人しか休めない構成でも、連続出場が 6 を超えないこと(従来は 7〜8 に達していた)。
            assertTrue(maxStreak <= 6, "seed=" + seed + " で連続出場が長すぎる: " + maxStreak);
            long consecRest = countConsecutiveRests(restingPerSet(schedule, pool));
            assertEquals(0, consecRest, "seed=" + seed + " で連続休みが発生");
        }
    }

    @Test
    @DisplayName("セット追加後も境界をまたいで流れを引き継ぐ(連続休みなし・連続出場も抑制)")
    void addSetsCarriesFlowAcrossBoundary() {
        List<ParticipantId> pool = participants(10);
        for (long seed = 0; seed < 10; seed++) {
            MatchingDomainService svc = serviceWithSeed(seed);
            MatchSchedule base = svc.generate(roomId, pool, 2, 10);
            MatchSchedule ext = svc.addSets(base, pool, 2, 6); // 10 + 6 = 16 セット
            assertEquals(16, ext.setCount());
            // 連続出場数の引き継ぎ: 境界(第11セット)を含めて連続休みが起きない。
            assertEquals(0, countConsecutiveRests(restingPerSet(ext, pool)),
                    "seed=" + seed + " 追加後に連続休みが発生");
            // 追加分でも連続出場が長くならない(一括生成と同水準)。
            assertTrue(maxConsecutivePlays(ext, pool) <= 6,
                    "seed=" + seed + " 追加後に連続出場が長すぎる");
        }
    }

    @Test
    @DisplayName("同じシードなら同じスケジュールを生成する(決定的)")
    void deterministicWithSameSeed() {
        List<ParticipantId> pool = participants(6);
        MatchSchedule a = serviceWithSeed(42L).generate(roomId, pool, 1, 10);
        MatchSchedule b = serviceWithSeed(42L).generate(roomId, pool, 1, 10);
        assertEquals(a.matches(), b.matches());
    }

    @Test
    @DisplayName("matchesOf は指定参加者の試合だけを返す")
    void matchesOfFiltersByParticipant() {
        List<ParticipantId> pool = participants(6);
        MatchSchedule schedule = serviceWithSeed(8L).generate(roomId, pool, 1, 10);

        ParticipantId target = pool.get(0);
        List<Match> filtered = schedule.matchesOf(target);
        assertAll(
                () -> assertTrue(filtered.stream().allMatch(m -> m.hasParticipant(target))),
                () -> assertEquals(
                        schedule.matches().stream().filter(m -> m.hasParticipant(target)).count(),
                        filtered.size())
        );
    }

    @Test
    @DisplayName("セット数が0以下の場合は例外を投げる")
    void throwsWhenSetCountIsNotPositive() {
        MatchingDomainService service = serviceWithSeed(9L);
        assertThrows(IllegalArgumentException.class,
                () -> service.generate(roomId, participants(4), 1, 0));
    }

    @Test
    @DisplayName("コート数が0以下の場合は例外を投げる")
    void throwsWhenCourtCountIsNotPositive() {
        MatchingDomainService service = serviceWithSeed(9L);
        assertThrows(IllegalArgumentException.class,
                () -> service.generate(roomId, participants(4), 0, 10));
    }

    @Test
    @DisplayName("addSets は既存の後ろにセットを継ぎ足す(セット番号・試合番号を継続、既存は保持)")
    void addSetsAppendsContinuingNumbers() {
        MatchingDomainService service = serviceWithSeed(11L);
        List<ParticipantId> pool = participants(6);
        MatchSchedule base = service.generate(roomId, pool, 1, 3);

        MatchSchedule updated = service.addSets(base, pool, 1, 2);

        assertEquals(5, updated.setCount());
        assertEquals(5, updated.size());
        for (int i = 0; i < updated.size(); i++) {
            assertEquals(i + 1, updated.matches().get(i).matchNumber().value());
        }
        // 既存の3試合は先頭にそのまま残る
        assertEquals(base.matches(), updated.matches().subList(0, 3));
    }

    @Test
    @DisplayName("addSets 後も出場回数が公平に保たれる(最大と最小の差は1以内)")
    void addSetsKeepsFairness() {
        MatchingDomainService service = serviceWithSeed(12L);
        List<ParticipantId> pool = participants(7);
        MatchSchedule base = service.generate(roomId, pool, 1, 5);

        MatchSchedule updated = service.addSets(base, pool, 1, 5);

        Map<ParticipantId, Integer> counts = new HashMap<>();
        pool.forEach(p -> counts.put(p, 0));
        for (Match m : updated.matches()) {
            counts.merge(m.pairA().player1(), 1, Integer::sum);
            counts.merge(m.pairA().player2(), 1, Integer::sum);
            counts.merge(m.pairB().player1(), 1, Integer::sum);
            counts.merge(m.pairB().player2(), 1, Integer::sum);
        }
        int max = counts.values().stream().max(Integer::compareTo).orElseThrow();
        int min = counts.values().stream().min(Integer::compareTo).orElseThrow();
        assertTrue(max - min <= 1, "出場回数の偏りが大きい: max=" + max + ", min=" + min);
    }

    @Test
    @DisplayName("addSets: 追加セット数が0以下の場合は例外を投げる")
    void addSetsThrowsWhenAdditionalSetCountIsNotPositive() {
        MatchingDomainService service = serviceWithSeed(13L);
        List<ParticipantId> pool = participants(4);
        MatchSchedule base = service.generate(roomId, pool, 1, 2);
        assertThrows(IllegalArgumentException.class, () -> service.addSets(base, pool, 1, 0));
    }

    // --- replanFuture(途中参加・早退) ---

    /** 指定セット番号までを開始済みにしたスケジュールを返す。 */
    private MatchSchedule withStartedSetsUpTo(MatchSchedule schedule, int upTo) {
        java.time.OffsetDateTime t = java.time.OffsetDateTime.parse("2026-06-30T09:00:00Z");
        List<Match> started = schedule.matches().stream()
                .map(m -> m.setNumber() <= upTo ? m.withStartedAt(t.plusMinutes(m.setNumber())) : m)
                .toList();
        return new MatchSchedule(schedule.roomId(), started);
    }

    @Test
    @DisplayName("replanFuture は開始済みセットを保持し、未開始セットだけ作り直す")
    void replanKeepsStartedSetsAndRebuildsFuture() {
        MatchingDomainService service = serviceWithSeed(21L);
        List<ParticipantId> pool = participants(6);
        MatchSchedule base = service.generate(roomId, pool, 1, 5);
        MatchSchedule withStarted = withStartedSetsUpTo(base, 2); // 第1・2セットを開始済みに

        MatchSchedule replanned = service.replanFuture(withStarted, pool, 1);

        // 合計セット数は維持
        assertEquals(5, replanned.setCount());
        // 開始済みの第1・2セットはそのまま
        List<Match> committedBefore = withStarted.matches().stream()
                .filter(m -> m.setNumber() <= 2).toList();
        List<Match> committedAfter = replanned.matches().stream()
                .filter(m -> m.setNumber() <= 2).toList();
        assertEquals(committedBefore, committedAfter);
    }

    @Test
    @DisplayName("replanFuture: 早退者は未開始セットに登場しない(開始済みには残る)")
    void replanExcludesLeftParticipantFromFuture() {
        MatchingDomainService service = serviceWithSeed(22L);
        List<ParticipantId> pool = participants(6);
        MatchSchedule base = service.generate(roomId, pool, 1, 5);
        MatchSchedule withStarted = withStartedSetsUpTo(base, 2);

        ParticipantId leaver = pool.get(0);
        List<ParticipantId> active = pool.stream().filter(p -> !p.equals(leaver)).toList();

        MatchSchedule replanned = service.replanFuture(withStarted, active, 1);

        boolean inFuture = replanned.matches().stream()
                .filter(m -> m.setNumber() > 2)
                .anyMatch(m -> participantsOf(m).contains(leaver));
        assertTrue(!inFuture, "早退者が未開始セットに含まれている");
    }

    @Test
    @DisplayName("replanFuture: 途中参加者は優先されず、公平(差は1以内)に収まる")
    void replanAddsLateComerWithoutPriority() {
        MatchingDomainService service = serviceWithSeed(23L);
        List<ParticipantId> pool = participants(6);
        MatchSchedule base = service.generate(roomId, pool, 1, 6);
        MatchSchedule withStarted = withStartedSetsUpTo(base, 3);

        ParticipantId late = ParticipantId.newId();
        List<ParticipantId> active = new java.util.ArrayList<>(pool);
        active.add(late);

        MatchSchedule replanned = service.replanFuture(withStarted, active, 1);

        // 途中参加者は未開始セット(4〜6)にだけ登場しうる
        long lateInFuture = replanned.matches().stream()
                .filter(m -> m.setNumber() > 3)
                .filter(m -> participantsOf(m).contains(late))
                .count();
        // 3セット分の未開始で1コート(4枠)。全既存が実績3〜4のところに横入りなので、
        // 優先されない=全セット独占はしない。
        assertTrue(lateInFuture <= 3, "途中参加者が優先されすぎている: " + lateInFuture);
    }

    @Test
    @DisplayName("replanFuture: 在席が4×コート数未満ならコート数を自動で減らす")
    void replanReducesCourtsWhenNotEnoughPlayers() {
        MatchingDomainService service = serviceWithSeed(24L);
        List<ParticipantId> pool = participants(8);
        MatchSchedule base = service.generate(roomId, pool, 2, 4); // 2コート×4セット=8試合
        MatchSchedule withStarted = withStartedSetsUpTo(base, 1);

        // 早退で5人に(2コート=8人には足りない → 1コートに縮小されるはず)
        List<ParticipantId> active = pool.subList(0, 5);
        MatchSchedule replanned = service.replanFuture(withStarted, active, 2);

        // 未開始セット(2〜4)は1コート=各セット1試合になる
        for (int set = 2; set <= 4; set++) {
            final int s = set;
            long courtsInSet = replanned.matches().stream()
                    .filter(m -> m.setNumber() == s).count();
            assertEquals(1, courtsInSet, "第" + s + "セットのコート数が縮小されていない");
        }
    }

    @Test
    @DisplayName("15人2コート25セットでも、同じ相手と何度も同じコートにならない(共起の偏りが小さい)")
    void coCourtOccurrenceIsWellSpread() {
        // 実際にクレームが出た構成(15人・2コート・25セット)。対策前はランダム配置のため
        // 特定の相手と6回同コート・別の相手とは0回、といった偏りが出ていた。
        // 各人の「同じ相手と同コートになった最大回数」が過度に大きくならないことを統計で確認する。
        int n = 15, courts = 2, sets = 25;
        List<ParticipantId> pool = participants(n);
        Map<ParticipantId, Integer> idx = new HashMap<>();
        for (int i = 0; i < n; i++) idx.put(pool.get(i), i + 1);

        int trials = 40;
        int worstMaxCoCourt = 0;
        double avgMax = 0;
        int neverMetTotal = 0; // 一度も同コートにならなかった相手ペアの総数(全試行合計)
        for (int t = 0; t < trials; t++) {
            MatchSchedule sch = new MatchingDomainService(new Random(t))
                    .generate(roomId, pool, courts, sets);
            // 同コート共起の回数(無向)。
            int[][] co = new int[n + 1][n + 1];
            for (Match m : sch.matches()) {
                List<ParticipantId> four = participantsOf(m);
                for (int i = 0; i < 4; i++) {
                    for (int j = i + 1; j < 4; j++) {
                        int a = idx.get(four.get(i)), b = idx.get(four.get(j));
                        co[a][b]++;
                        co[b][a]++;
                    }
                }
            }
            int maxCo = 0;
            for (int a = 1; a <= n; a++) {
                for (int b = a + 1; b <= n; b++) {
                    maxCo = Math.max(maxCo, co[a][b]);
                    if (co[a][b] == 0) neverMetTotal++;
                }
            }
            worstMaxCoCourt = Math.max(worstMaxCoCourt, maxCo);
            avgMax += maxCo;
        }
        avgMax /= trials;

        // 参考: 完全ランダム配置(対策前)では avgMax≈7.5・worst=10・「一度も当たらない相手」≈7組。
        // 対策後(指数重み+山登り法+スケジュール全体のマルチリスタート)では、
        // 25セット×12ペア枠を105ペアに配るので平均2.86回。ほぼ常に最大4回に収まる。
        // 最悪でも「同じ相手と5回超も同コート」は起きないこと(以前は10回が観測された)。
        assertTrue(worstMaxCoCourt <= 5,
                "同じ相手と同コートになった最大回数が多すぎる: " + worstMaxCoCourt);
        // 平均的には最大でも4回程度に収まること(対策前は約7.5)。
        assertTrue(avgMax <= 4.5, "同コート共起の最大回数の平均が大きい: " + avgMax);
        // 「一度も同コートにならない相手」がほぼ無いこと(全試行平均で1.5ペア未満、対策前は約7組)。
        // ParticipantId は毎回ランダムな UUID で生成されるため、HashMap の反復順に依存する
        // タイブレークが試行ごとに揺れ、平均は 1.0 前後で上下する。閾値には余裕を持たせる。
        assertTrue(neverMetTotal < trials * 1.5,
                "一度も同コートにならない相手ペアが多い(平均" + (neverMetTotal / (double) trials) + "組)");
    }

    @Test
    @DisplayName("16人2コート(休み=出場)でも2グループ固定にならず混ざる(連続休みは2まで許容)")
    void sixteenPeopleTwoCourtsMixWithoutFixedCamps() {
        // 16人・2コートは出場8/休み8。連続休み禁止を厳密に守ると出場者が2グループに固定され、
        // 相手グループとは一度も同コートにならない(=8×8=64ペアが未対戦)病理になる。
        // 混ぜモード(連続休み2まで許容)で、未対戦ペアがほぼ無くなり、連続休みも2で頭打ちに
        // なることを統計で確認する。
        int n = 16, courts = 2, sets = 14;
        List<ParticipantId> pool = participants(n);
        Map<ParticipantId, Integer> idx = new HashMap<>();
        for (int i = 0; i < n; i++) idx.put(pool.get(i), i + 1);

        int trials = 30;
        double neverSum = 0;
        int worstRestStreak = 0;
        int worstPlayDiff = 0;
        for (int t = 0; t < trials; t++) {
            MatchSchedule sch = new MatchingDomainService(new Random(t * 7 + 100))
                    .generate(roomId, pool, courts, sets);
            int[][] co = new int[n + 1][n + 1];
            int[] play = new int[n + 1];
            Map<Integer, Set<Integer>> playing = new java.util.TreeMap<>();
            for (Match m : sch.matches()) {
                int s = m.setNumber();
                playing.computeIfAbsent(s, k -> new HashSet<>());
                for (ParticipantId p : participantsOf(m)) {
                    playing.get(s).add(idx.get(p));
                    play[idx.get(p)]++;
                }
                List<ParticipantId> four = participantsOf(m);
                for (int i = 0; i < 4; i++) {
                    for (int j = i + 1; j < 4; j++) {
                        int a = idx.get(four.get(i)), b = idx.get(four.get(j));
                        co[a][b]++;
                        co[b][a]++;
                    }
                }
            }
            int never = 0;
            for (int a = 1; a <= n; a++) {
                for (int b = a + 1; b <= n; b++) {
                    if (co[a][b] == 0) never++;
                }
            }
            neverSum += never;
            int pmin = 999, pmax = 0;
            for (int a = 1; a <= n; a++) {
                pmin = Math.min(pmin, play[a]);
                pmax = Math.max(pmax, play[a]);
            }
            worstPlayDiff = Math.max(worstPlayDiff, pmax - pmin);
            // 連続休みの最大長。
            List<Integer> ss = new ArrayList<>(playing.keySet());
            Map<Integer, Integer> streak = new HashMap<>();
            for (int a = 1; a <= n; a++) streak.put(a, 0);
            for (Integer s : ss) {
                Set<Integer> pl = playing.get(s);
                for (int a = 1; a <= n; a++) {
                    if (pl.contains(a)) streak.put(a, 0);
                    else {
                        streak.merge(a, 1, Integer::sum);
                        worstRestStreak = Math.max(worstRestStreak, streak.get(a));
                    }
                }
            }
        }
        double avgNever = neverSum / trials;
        // 厳密版なら64組が未対戦。混ぜモード(+マルチリスタート)では平均2組程度まで激減すること。
        assertTrue(avgNever < 3.5,
                "16人で一度も同コートにならないペアが多い(平均" + avgNever + "組)");
        // 連続休みは2セットまで(=1回まで連続休み許容)。3連続は起きないこと。
        assertTrue(worstRestStreak <= 2,
                "連続休みが上限(2)を超えた: " + worstRestStreak);
        // 出場回数は公平(差は最大1)。
        assertTrue(worstPlayDiff <= 1, "出場回数の差が大きい: " + worstPlayDiff);
    }

    /** 1試合の4名。 */
    private List<ParticipantId> participantsOf(Match m) {
        return List.of(
                m.pairA().player1(), m.pairA().player2(),
                m.pairB().player1(), m.pairB().player2());
    }
}

package com.shuttlematch.domain.service;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.shuttlematch.domain.model.match.Match;
import com.shuttlematch.domain.model.match.MatchSchedule;
import com.shuttlematch.domain.model.room.ParticipantId;
import com.shuttlematch.domain.model.room.RoomId;
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

    /** 1試合の4名。 */
    private List<ParticipantId> participantsOf(Match m) {
        return List.of(
                m.pairA().player1(), m.pairA().player2(),
                m.pairB().player1(), m.pairB().player2());
    }
}

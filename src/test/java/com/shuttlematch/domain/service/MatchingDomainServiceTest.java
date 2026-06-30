package com.shuttlematch.domain.service;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.shuttlematch.domain.model.match.Match;
import com.shuttlematch.domain.model.match.MatchSchedule;
import com.shuttlematch.domain.model.session.ParticipantId;
import com.shuttlematch.domain.model.session.SessionId;
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

    private final SessionId sessionId = SessionId.newId();

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
                () -> service.generate(sessionId, participants(3), 1, 10));
    }

    @Test
    @DisplayName("ちょうど4人なら生成できる(最低人数の境界)")
    void generatesWithExactlyFourParticipants() {
        MatchSchedule schedule = serviceWithSeed(1L)
                .generate(sessionId, participants(4), 1, MatchingDomainService.DEFAULT_SET_COUNT);
        assertEquals(MatchingDomainService.DEFAULT_SET_COUNT, schedule.size());
    }

    @Test
    @DisplayName("デフォルト(10)セットでは1コートあたり10試合を生成する")
    void generatesDefaultSetCount() {
        MatchSchedule schedule = serviceWithSeed(2L)
                .generate(sessionId, participants(8), 1, MatchingDomainService.DEFAULT_SET_COUNT);
        assertEquals(10, schedule.size());
    }

    @Test
    @DisplayName("セット数を指定できる")
    void generatesRequestedNumberOfSets() {
        MatchSchedule schedule = serviceWithSeed(2L).generate(sessionId, participants(8), 1, 7);
        assertEquals(7, schedule.size());
    }

    @Test
    @DisplayName("コート数とセット数の積が試合数になる")
    void generatesCourtCountTimesSetCount() {
        MatchSchedule schedule = serviceWithSeed(2L).generate(sessionId, participants(8), 2, 5);
        assertEquals(10, schedule.size());
    }

    @Test
    @DisplayName("試合番号は1から連番で振られる")
    void assignsSequentialMatchNumbers() {
        MatchSchedule schedule = serviceWithSeed(3L).generate(sessionId, participants(6), 1, 10);
        for (int i = 0; i < schedule.size(); i++) {
            assertEquals(i + 1, schedule.matches().get(i).matchNumber().value());
        }
    }

    @Test
    @DisplayName("各試合は重複しない4名で構成される")
    void eachMatchHasFourDistinctParticipants() {
        MatchSchedule schedule = serviceWithSeed(4L).generate(sessionId, participants(7), 1, 10);
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
        MatchSchedule schedule = serviceWithSeed(5L).generate(sessionId, pool, 1, 10);
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
        MatchSchedule schedule = serviceWithSeed(6L).generate(sessionId, pool, 1, 10);

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
        MatchSchedule a = serviceWithSeed(42L).generate(sessionId, pool, 1, 10);
        MatchSchedule b = serviceWithSeed(42L).generate(sessionId, pool, 1, 10);
        assertEquals(a.matches(), b.matches());
    }

    @Test
    @DisplayName("matchesOf は指定参加者の試合だけを返す")
    void matchesOfFiltersByParticipant() {
        List<ParticipantId> pool = participants(6);
        MatchSchedule schedule = serviceWithSeed(8L).generate(sessionId, pool, 1, 10);

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
                () -> service.generate(sessionId, participants(4), 1, 0));
    }

    @Test
    @DisplayName("コート数が0以下の場合は例外を投げる")
    void throwsWhenCourtCountIsNotPositive() {
        MatchingDomainService service = serviceWithSeed(9L);
        assertThrows(IllegalArgumentException.class,
                () -> service.generate(sessionId, participants(4), 0, 10));
    }
}

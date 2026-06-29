package com.shuttlematch.application.usecase.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shuttlematch.domain.model.match.MatchSchedule;
import com.shuttlematch.domain.model.session.ParticipantId;
import com.shuttlematch.domain.model.session.SessionId;
import com.shuttlematch.domain.repository.MatchScheduleRepository;
import com.shuttlematch.domain.repository.SessionParticipantRepository;
import com.shuttlematch.domain.service.MatchingDomainService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GenerateMatchesUseCaseTest {

    private FakeSessionParticipantRepository participantRepository;
    private FakeMatchScheduleRepository matchScheduleRepository;
    private GenerateMatchesUseCase useCase;
    private final SessionId sessionId = SessionId.newId();

    @BeforeEach
    void setUp() {
        participantRepository = new FakeSessionParticipantRepository();
        matchScheduleRepository = new FakeMatchScheduleRepository();
        // シード固定で決定的にする
        useCase = new GenerateMatchesUseCase(
                participantRepository,
                matchScheduleRepository,
                new MatchingDomainService(new Random(100L)));
    }

    private List<ParticipantId> participants(int count) {
        return IntStream.range(0, count).mapToObj(i -> ParticipantId.newId()).toList();
    }

    @Test
    @DisplayName("参加者を読み込み、生成したスケジュールを保存して返す")
    void generatesAndPersistsSchedule() {
        participantRepository.setParticipants(sessionId, participants(8));

        MatchSchedule result = useCase.execute(new GenerateMatchesCommand(sessionId));

        assertThat(result.size()).isEqualTo(15);
        assertThat(result.sessionId()).isEqualTo(sessionId);
        // 永続化されたものと一致する
        assertThat(matchScheduleRepository.findBySessionId(sessionId)).contains(result);
    }

    @Test
    @DisplayName("試合数を指定して生成できる")
    void generatesWithCustomMatchCount() {
        participantRepository.setParticipants(sessionId, participants(6));

        MatchSchedule result = useCase.execute(new GenerateMatchesCommand(sessionId, 5));

        assertThat(result.size()).isEqualTo(5);
    }

    @Test
    @DisplayName("再生成時は既存スケジュールを削除してから保存する")
    void regenerationDeletesExistingSchedule() {
        participantRepository.setParticipants(sessionId, participants(8));

        useCase.execute(new GenerateMatchesCommand(sessionId));
        useCase.execute(new GenerateMatchesCommand(sessionId));

        // 削除が呼ばれ、最終的に1件だけ残る
        assertThat(matchScheduleRepository.deleteCount).isEqualTo(2);
        assertThat(matchScheduleRepository.count(sessionId)).isEqualTo(1);
    }

    @Test
    @DisplayName("参加者が4人未満なら例外を投げ、保存しない")
    void doesNotPersistWhenTooFewParticipants() {
        participantRepository.setParticipants(sessionId, participants(3));

        assertThatThrownBy(() -> useCase.execute(new GenerateMatchesCommand(sessionId)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(matchScheduleRepository.findBySessionId(sessionId)).isEmpty();
    }

    // --- 以下、テスト用のインメモリ実装 ---

    private static final class FakeSessionParticipantRepository implements SessionParticipantRepository {
        private final Map<SessionId, List<ParticipantId>> store = new HashMap<>();

        void setParticipants(SessionId sessionId, List<ParticipantId> participants) {
            store.put(sessionId, participants);
        }

        @Override
        public List<ParticipantId> findParticipantIds(SessionId sessionId) {
            return store.getOrDefault(sessionId, List.of());
        }
    }

    private static final class FakeMatchScheduleRepository implements MatchScheduleRepository {
        private final List<MatchSchedule> store = new ArrayList<>();
        int deleteCount = 0;

        @Override
        public MatchSchedule save(MatchSchedule schedule) {
            store.add(schedule);
            return schedule;
        }

        @Override
        public Optional<MatchSchedule> findBySessionId(SessionId sessionId) {
            return store.stream()
                    .filter(s -> s.sessionId().equals(sessionId))
                    .reduce((first, second) -> second); // 最後に保存されたものを返す
        }

        @Override
        public void deleteBySessionId(SessionId sessionId) {
            deleteCount++;
            store.removeIf(s -> s.sessionId().equals(sessionId));
        }

        long count(SessionId sessionId) {
            return store.stream().filter(s -> s.sessionId().equals(sessionId)).count();
        }
    }
}

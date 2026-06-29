package com.shuttlematch.infrastructure.persistence.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import com.shuttlematch.TestcontainersConfiguration;
import com.shuttlematch.domain.model.match.MatchSchedule;
import com.shuttlematch.domain.model.session.ParticipantId;
import com.shuttlematch.domain.model.session.SessionId;
import com.shuttlematch.domain.repository.MatchScheduleRepository;
import com.shuttlematch.domain.repository.SessionRepository;
import com.shuttlematch.domain.service.MatchingDomainService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.DockerClientFactory;

/**
 * 試合スケジュール永続化の結合テスト(実 PostgreSQL を Testcontainers で起動)。
 * Docker が利用できない環境ではクラスごとスキップされる。
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
@EnabledIf(value = "isDockerAvailable", disabledReason = "Docker が必要です")
class MatchSchedulePersistenceIT {

    static boolean isDockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            return false;
        }
    }

    @PersistenceContext
    private EntityManager em;

    @Autowired
    private MatchScheduleRepository matchScheduleRepository;

    @Autowired
    private SessionRepository sessionRepository;

    /** users → circles → sessions → session_participants を投入し、参加者 ID を返す。 */
    private List<ParticipantId> seedSessionWithParticipants(SessionId sessionId, int participantCount) {
        UUID userId = UUID.randomUUID();
        em.createNativeQuery("insert into users(id, name, email) values (?1, ?2, ?3)")
                .setParameter(1, userId)
                .setParameter(2, "Tester")
                .setParameter(3, "tester+" + userId + "@example.com")
                .executeUpdate();

        UUID circleId = UUID.randomUUID();
        em.createNativeQuery("insert into circles(id, name, invite_code, created_by) values (?1, ?2, ?3, ?4)")
                .setParameter(1, circleId)
                .setParameter(2, "テストサークル")
                .setParameter(3, "INV-" + UUID.randomUUID().toString().substring(0, 8))
                .setParameter(4, userId)
                .executeUpdate();

        em.createNativeQuery(
                        "insert into sessions(id, circle_id, title, held_at, created_by) "
                                + "values (?1, ?2, ?3, now(), ?4)")
                .setParameter(1, sessionId.value())
                .setParameter(2, circleId)
                .setParameter(3, "テスト練習会")
                .setParameter(4, userId)
                .executeUpdate();

        return IntStream.range(0, participantCount)
                .mapToObj(i -> {
                    UUID participantId = UUID.randomUUID();
                    em.createNativeQuery(
                                    "insert into session_participants(id, session_id, guest_name) "
                                            + "values (?1, ?2, ?3)")
                            .setParameter(1, participantId)
                            .setParameter(2, sessionId.value())
                            .setParameter(3, "ゲスト" + (i + 1))
                            .executeUpdate();
                    return ParticipantId.of(participantId);
                })
                .toList();
    }

    @Test
    @DisplayName("セッションを参加者ごと復元できる")
    void loadsSessionWithParticipants() {
        SessionId sessionId = SessionId.newId();
        List<ParticipantId> seeded = seedSessionWithParticipants(sessionId, 6);
        em.flush();
        em.clear();

        List<ParticipantId> found = sessionRepository.findById(sessionId).orElseThrow().participantIds();

        assertThat(found).containsExactlyInAnyOrderElementsOf(seeded);
    }

    @Test
    @DisplayName("生成したスケジュールを保存し、取得して同じ内容に復元できる")
    void savesAndRestoresSchedule() {
        SessionId sessionId = SessionId.newId();
        List<ParticipantId> participants = seedSessionWithParticipants(sessionId, 6);
        em.flush();

        MatchSchedule generated = new MatchingDomainService(new Random(1L))
                .generate(sessionId, participants, 10);

        matchScheduleRepository.save(generated);
        em.flush();
        em.clear(); // 一次キャッシュを破棄して DB から読み直す

        MatchSchedule restored = matchScheduleRepository.findBySessionId(sessionId).orElseThrow();
        assertThat(restored.size()).isEqualTo(10);
        assertThat(restored.matches()).isEqualTo(generated.matches());
    }

    @Test
    @DisplayName("既存スケジュールを削除でき、再生成しても1件だけ残る")
    void deletesAndRegeneratesSchedule() {
        SessionId sessionId = SessionId.newId();
        List<ParticipantId> participants = seedSessionWithParticipants(sessionId, 8);
        em.flush();

        MatchingDomainService service = new MatchingDomainService(new Random(2L));
        matchScheduleRepository.save(service.generate(sessionId, participants, 15));
        em.flush();

        matchScheduleRepository.deleteBySessionId(sessionId);
        em.flush();
        assertThat(matchScheduleRepository.findBySessionId(sessionId)).isEmpty();

        matchScheduleRepository.save(service.generate(sessionId, participants, 15));
        em.flush();
        em.clear();

        Long scheduleCount = ((Number) em.createNativeQuery(
                        "select count(*) from match_schedules where session_id = ?1")
                .setParameter(1, sessionId.value())
                .getSingleResult()).longValue();
        assertThat(scheduleCount).isEqualTo(1L);
    }
}

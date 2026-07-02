package com.shuttlematch.infrastructure.persistence.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import com.shuttlematch.TestcontainersConfiguration;
import com.shuttlematch.domain.model.match.MatchSchedule;
import com.shuttlematch.domain.model.room.ParticipantId;
import com.shuttlematch.domain.model.room.RoomId;
import com.shuttlematch.domain.repository.MatchScheduleRepository;
import com.shuttlematch.domain.repository.RoomRepository;
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
    private RoomRepository roomRepository;

    /** users → rooms → room_participants を投入し、参加者 ID を返す。 */
    private List<ParticipantId> seedSessionWithParticipants(RoomId roomId, int participantCount) {
        UUID userId = UUID.randomUUID();
        em.createNativeQuery("insert into users(id, name, email) values (?1, ?2, ?3)")
                .setParameter(1, userId)
                .setParameter(2, "Tester")
                .setParameter(3, "tester+" + userId + "@example.com")
                .executeUpdate();

        em.createNativeQuery(
                        "insert into rooms(id, title, held_at, created_by) "
                                + "values (?1, ?2, now(), ?3)")
                .setParameter(1, roomId.value())
                .setParameter(2, "テスト練習会")
                .setParameter(3, userId)
                .executeUpdate();

        return IntStream.range(0, participantCount)
                .mapToObj(i -> {
                    UUID participantId = UUID.randomUUID();
                    em.createNativeQuery(
                                    "insert into room_participants(id, room_id, guest_name) "
                                            + "values (?1, ?2, ?3)")
                            .setParameter(1, participantId)
                            .setParameter(2, roomId.value())
                            .setParameter(3, "ゲスト" + (i + 1))
                            .executeUpdate();
                    return ParticipantId.of(participantId);
                })
                .toList();
    }

    @Test
    @DisplayName("セッションを参加者ごと復元できる")
    void loadsSessionWithParticipants() {
        RoomId roomId = RoomId.newId();
        List<ParticipantId> seeded = seedSessionWithParticipants(roomId, 6);
        em.flush();
        em.clear();

        List<ParticipantId> found = roomRepository.findById(roomId).orElseThrow().participantIds();

        assertThat(found).containsExactlyInAnyOrderElementsOf(seeded);
    }

    @Test
    @DisplayName("生成したスケジュールを保存し、取得して同じ内容に復元できる")
    void savesAndRestoresSchedule() {
        RoomId roomId = RoomId.newId();
        List<ParticipantId> participants = seedSessionWithParticipants(roomId, 6);
        em.flush();

        MatchSchedule generated = new MatchingDomainService(new Random(1L))
                .generate(roomId, participants, 1, 10);

        matchScheduleRepository.save(generated);
        em.flush();
        em.clear(); // 一次キャッシュを破棄して DB から読み直す

        MatchSchedule restored = matchScheduleRepository.findByRoomId(roomId).orElseThrow();
        assertThat(restored.size()).isEqualTo(10);
        assertThat(restored.matches()).isEqualTo(generated.matches());
    }

    @Test
    @DisplayName("セット開始で、そのセットの全コートに started_at が記録される")
    void startSetRecordsStartedAtForAllCourts() {
        RoomId roomId = RoomId.newId();
        // 2コート分(8名)。第1セット=試合1,2 / 第2セット=試合3,4 ...
        List<ParticipantId> participants = seedSessionWithParticipants(roomId, 8);
        em.flush();

        matchScheduleRepository.save(
                new MatchingDomainService(new Random(3L)).generate(roomId, participants, 2, 5));
        em.flush();
        em.clear();

        java.time.OffsetDateTime now = java.time.OffsetDateTime.now();
        matchScheduleRepository.startSet(roomId, 2, now);
        em.flush();
        em.clear();

        MatchSchedule restored = matchScheduleRepository.findByRoomId(roomId).orElseThrow();
        // 第2セットの全試合(全コート)が開始済み
        assertThat(restored.matches().stream().filter(m -> m.setNumber() == 2))
                .isNotEmpty()
                .allMatch(m -> m.isStarted());
        // 第1セットは未開始のまま
        assertThat(restored.matches().stream().filter(m -> m.setNumber() == 1))
                .noneMatch(m -> m.isStarted());
    }

    @Test
    @DisplayName("既存スケジュールを削除でき、再生成しても1件だけ残る")
    void deletesAndRegeneratesSchedule() {
        RoomId roomId = RoomId.newId();
        List<ParticipantId> participants = seedSessionWithParticipants(roomId, 8);
        em.flush();

        MatchingDomainService service = new MatchingDomainService(new Random(2L));
        matchScheduleRepository.save(service.generate(roomId, participants, 1, 15));
        em.flush();

        matchScheduleRepository.deleteByRoomId(roomId);
        em.flush();
        assertThat(matchScheduleRepository.findByRoomId(roomId)).isEmpty();

        matchScheduleRepository.save(service.generate(roomId, participants, 1, 15));
        em.flush();
        em.clear();

        Long scheduleCount = ((Number) em.createNativeQuery(
                        "select count(*) from match_schedules where room_id = ?1")
                .setParameter(1, roomId.value())
                .getSingleResult()).longValue();
        assertThat(scheduleCount).isEqualTo(1L);
    }
}

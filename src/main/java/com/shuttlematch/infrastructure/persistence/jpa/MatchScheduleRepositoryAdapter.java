package com.shuttlematch.infrastructure.persistence.jpa;

import com.shuttlematch.domain.model.match.Match;
import com.shuttlematch.domain.model.match.MatchNumber;
import com.shuttlematch.domain.model.match.MatchSchedule;
import com.shuttlematch.domain.model.match.Pair;
import com.shuttlematch.domain.model.session.ParticipantId;
import com.shuttlematch.domain.model.session.SessionId;
import com.shuttlematch.domain.repository.MatchScheduleRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * {@link MatchScheduleRepository} の JPA 実装。ドメイン⇔エンティティの変換を担う。
 */
@Repository
public class MatchScheduleRepositoryAdapter implements MatchScheduleRepository {

    private final MatchScheduleJpaRepository jpaRepository;

    public MatchScheduleRepositoryAdapter(MatchScheduleJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public MatchSchedule save(MatchSchedule schedule) {
        MatchScheduleEntity saved = jpaRepository.save(toEntity(schedule));
        return toDomain(saved);
    }

    @Override
    public Optional<MatchSchedule> findBySessionId(SessionId sessionId) {
        return jpaRepository.findBySessionId(sessionId.value()).map(this::toDomain);
    }

    @Override
    public void deleteBySessionId(SessionId sessionId) {
        jpaRepository.deleteBySessionId(sessionId.value());
    }

    @Override
    public Optional<MatchSchedule> startMatch(SessionId sessionId, int matchNumber, OffsetDateTime startedAt) {
        return jpaRepository.findBySessionId(sessionId.value())
                .flatMap(schedule -> {
                    Optional<MatchEntity> target = schedule.getMatches().stream()
                            .filter(m -> m.getMatchNumber() == matchNumber)
                            .findFirst();
                    target.ifPresent(m -> m.setStartedAt(startedAt));
                    // JPA のダーティチェックで started_at が更新される
                    return target.map(m -> toDomain(jpaRepository.save(schedule)));
                });
    }

    private MatchScheduleEntity toEntity(MatchSchedule schedule) {
        MatchScheduleEntity entity = new MatchScheduleEntity();
        entity.setId(UUID.randomUUID());
        entity.setSessionId(schedule.sessionId().value());
        for (Match match : schedule.matches()) {
            entity.addMatch(toEntity(match));
        }
        return entity;
    }

    private MatchEntity toEntity(Match match) {
        MatchEntity entity = new MatchEntity();
        entity.setId(UUID.randomUUID());
        entity.setMatchNumber(match.matchNumber().value());
        entity.setSetNumber(match.setNumber());
        entity.setPairAPlayer1Id(match.pairA().player1().value());
        entity.setPairAPlayer2Id(match.pairA().player2().value());
        entity.setPairBPlayer1Id(match.pairB().player1().value());
        entity.setPairBPlayer2Id(match.pairB().player2().value());
        entity.setCourtNumber(match.courtNumber());
        entity.setStartedAt(match.startedAt());
        return entity;
    }

    private MatchSchedule toDomain(MatchScheduleEntity entity) {
        List<Match> matches = entity.getMatches().stream()
                .map(this::toDomain)
                .toList();
        return new MatchSchedule(SessionId.of(entity.getSessionId()), matches);
    }

    private Match toDomain(MatchEntity entity) {
        Pair pairA = new Pair(
                ParticipantId.of(entity.getPairAPlayer1Id()),
                ParticipantId.of(entity.getPairAPlayer2Id()));
        Pair pairB = new Pair(
                ParticipantId.of(entity.getPairBPlayer1Id()),
                ParticipantId.of(entity.getPairBPlayer2Id()));
        int courtNumber = entity.getCourtNumber() != null ? entity.getCourtNumber() : 1;
        return new Match(MatchNumber.of(entity.getMatchNumber()), entity.getSetNumber(), courtNumber,
                pairA, pairB, entity.getStartedAt());
    }
}

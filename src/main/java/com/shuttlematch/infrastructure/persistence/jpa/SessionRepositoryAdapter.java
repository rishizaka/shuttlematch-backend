package com.shuttlematch.infrastructure.persistence.jpa;

import com.shuttlematch.domain.model.circle.CircleId;
import com.shuttlematch.domain.model.session.Participant;
import com.shuttlematch.domain.model.session.ParticipantId;
import com.shuttlematch.domain.model.session.Session;
import com.shuttlematch.domain.model.session.SessionId;
import com.shuttlematch.domain.model.session.SessionStatus;
import com.shuttlematch.domain.model.user.UserId;
import com.shuttlematch.domain.repository.SessionRepository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

/**
 * {@link SessionRepository} の JPA 実装。
 * <p>
 * Session 集約と session_participants を 1 つの集約として扱うため、保存時に
 * 参加者コレクションの差分(追加・削除)を調整する。
 */
@Repository
public class SessionRepositoryAdapter implements SessionRepository {

    private final SessionJpaRepository sessionJpaRepository;
    private final SessionParticipantJpaRepository participantJpaRepository;

    public SessionRepositoryAdapter(
            SessionJpaRepository sessionJpaRepository,
            SessionParticipantJpaRepository participantJpaRepository) {
        this.sessionJpaRepository = sessionJpaRepository;
        this.participantJpaRepository = participantJpaRepository;
    }

    @Override
    public Session save(Session session) {
        SessionEntity entity = sessionJpaRepository.findById(session.id().value())
                .orElseGet(SessionEntity::new);
        entity.setId(session.id().value());
        entity.setCircleId(session.circleId().value());
        entity.setTitle(session.title());
        entity.setHeldAt(session.heldAt());
        entity.setLocation(session.location());
        entity.setCapacity(session.capacity());
        entity.setStatus(session.status().name());
        entity.setCreatedBy(session.createdBy().value());
        sessionJpaRepository.save(entity);

        reconcileParticipants(session);

        return findById(session.id()).orElseThrow();
    }

    private void reconcileParticipants(Session session) {
        UUID sessionId = session.id().value();
        List<SessionParticipantEntity> existing = participantJpaRepository.findBySessionId(sessionId);
        Set<UUID> existingIds = existing.stream()
                .map(SessionParticipantEntity::getId)
                .collect(Collectors.toSet());
        Set<UUID> desiredIds = session.participants().stream()
                .map(p -> p.id().value())
                .collect(Collectors.toSet());

        // 集約から取り除かれた参加者を削除
        List<SessionParticipantEntity> toDelete = existing.stream()
                .filter(e -> !desiredIds.contains(e.getId()))
                .toList();
        if (!toDelete.isEmpty()) {
            participantJpaRepository.deleteAll(toDelete);
        }

        // 新規参加者を追加
        for (Participant participant : session.participants()) {
            if (!existingIds.contains(participant.id().value())) {
                participantJpaRepository.save(toEntity(sessionId, participant));
            }
        }
    }

    @Override
    public Optional<Session> findById(SessionId sessionId) {
        return sessionJpaRepository.findById(sessionId.value())
                .map(this::toDomain);
    }

    private SessionParticipantEntity toEntity(UUID sessionId, Participant participant) {
        SessionParticipantEntity entity = new SessionParticipantEntity();
        entity.setId(participant.id().value());
        entity.setSessionId(sessionId);
        entity.setUserId(participant.userId() == null ? null : participant.userId().value());
        entity.setGuestName(participant.guestName());
        return entity;
    }

    private Session toDomain(SessionEntity entity) {
        List<Participant> participants = participantJpaRepository.findBySessionId(entity.getId()).stream()
                .map(this::toParticipant)
                .toList();
        return Session.reconstitute(
                SessionId.of(entity.getId()),
                CircleId.of(entity.getCircleId()),
                entity.getTitle(),
                entity.getHeldAt(),
                entity.getLocation(),
                entity.getCapacity(),
                SessionStatus.valueOf(entity.getStatus()),
                UserId.of(entity.getCreatedBy()),
                participants);
    }

    private Participant toParticipant(SessionParticipantEntity entity) {
        UserId userId = entity.getUserId() == null ? null : UserId.of(entity.getUserId());
        return Participant.reconstitute(ParticipantId.of(entity.getId()), userId, entity.getGuestName());
    }
}

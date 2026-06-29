package com.shuttlematch.infrastructure.persistence.jpa;

import com.shuttlematch.domain.model.session.ParticipantId;
import com.shuttlematch.domain.model.session.SessionId;
import com.shuttlematch.domain.repository.SessionParticipantRepository;

import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * {@link SessionParticipantRepository} の JPA 実装。
 */
@Repository
public class SessionParticipantRepositoryAdapter implements SessionParticipantRepository {

    private final SessionParticipantJpaRepository jpaRepository;

    public SessionParticipantRepositoryAdapter(SessionParticipantJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public List<ParticipantId> findParticipantIds(SessionId sessionId) {
        return jpaRepository.findBySessionId(sessionId.value()).stream()
                .map(entity -> ParticipantId.of(entity.getId()))
                .toList();
    }
}

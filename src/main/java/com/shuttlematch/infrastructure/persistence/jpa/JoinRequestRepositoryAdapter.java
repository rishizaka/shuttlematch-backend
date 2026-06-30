package com.shuttlematch.infrastructure.persistence.jpa;

import com.shuttlematch.domain.model.circle.CircleId;
import com.shuttlematch.domain.model.circle.JoinRequest;
import com.shuttlematch.domain.model.circle.JoinRequestId;
import com.shuttlematch.domain.model.circle.JoinRequestStatus;
import com.shuttlematch.domain.model.user.UserId;
import com.shuttlematch.domain.repository.JoinRequestRepository;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * {@link JoinRequestRepository} の JPA 実装。
 */
@Repository
public class JoinRequestRepositoryAdapter implements JoinRequestRepository {

    private final JoinRequestJpaRepository jpaRepository;

    public JoinRequestRepositoryAdapter(JoinRequestJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public JoinRequest save(JoinRequest joinRequest) {
        JoinRequestEntity entity = jpaRepository.findById(joinRequest.id().value())
                .orElseGet(JoinRequestEntity::new);
        entity.setId(joinRequest.id().value());
        entity.setCircleId(joinRequest.circleId().value());
        entity.setUserId(joinRequest.userId().value());
        entity.setStatus(joinRequest.status().name());
        entity.setRequestedAt(joinRequest.requestedAt());
        entity.setDecidedAt(joinRequest.decidedAt());
        jpaRepository.save(entity);
        return findById(joinRequest.id()).orElseThrow();
    }

    @Override
    public Optional<JoinRequest> findById(JoinRequestId id) {
        return jpaRepository.findById(id.value()).map(this::toDomain);
    }

    @Override
    public List<JoinRequest> findByCircleIdAndStatus(CircleId circleId, JoinRequestStatus status) {
        return jpaRepository
                .findByCircleIdAndStatusOrderByRequestedAtAsc(circleId.value(), status.name()).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public boolean existsPending(CircleId circleId, UserId userId) {
        return jpaRepository.existsByCircleIdAndUserIdAndStatus(
                circleId.value(), userId.value(), JoinRequestStatus.PENDING.name());
    }

    private JoinRequest toDomain(JoinRequestEntity entity) {
        return JoinRequest.reconstitute(
                JoinRequestId.of(entity.getId()),
                CircleId.of(entity.getCircleId()),
                UserId.of(entity.getUserId()),
                JoinRequestStatus.valueOf(entity.getStatus()),
                entity.getRequestedAt(),
                entity.getDecidedAt());
    }
}

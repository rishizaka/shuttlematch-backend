package com.shuttlematch.infrastructure.persistence.jpa;

import com.shuttlematch.domain.model.circle.Circle;
import com.shuttlematch.domain.model.circle.CircleId;
import com.shuttlematch.domain.model.circle.InviteCode;
import com.shuttlematch.domain.model.circle.JoinPolicy;
import com.shuttlematch.domain.model.circle.Member;
import com.shuttlematch.domain.model.circle.MemberRole;
import com.shuttlematch.domain.model.user.UserId;
import com.shuttlematch.domain.repository.CircleRepository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

/**
 * {@link CircleRepository} の JPA 実装。サークルとメンバーを1つの集約として保存・復元する。
 */
@Repository
public class CircleRepositoryAdapter implements CircleRepository {

    private final CircleJpaRepository circleJpaRepository;
    private final CircleMemberJpaRepository memberJpaRepository;

    public CircleRepositoryAdapter(
            CircleJpaRepository circleJpaRepository,
            CircleMemberJpaRepository memberJpaRepository) {
        this.circleJpaRepository = circleJpaRepository;
        this.memberJpaRepository = memberJpaRepository;
    }

    @Override
    public Circle save(Circle circle) {
        CircleEntity entity = circleJpaRepository.findById(circle.id().value())
                .orElseGet(CircleEntity::new);
        entity.setId(circle.id().value());
        entity.setName(circle.name());
        entity.setDescription(circle.description());
        entity.setInviteCode(circle.inviteCode().value());
        entity.setJoinPolicy(circle.joinPolicy().name());
        entity.setCreatedBy(circle.createdBy().value());
        circleJpaRepository.save(entity);

        reconcileMembers(circle);

        return findById(circle.id()).orElseThrow();
    }

    private void reconcileMembers(Circle circle) {
        UUID circleId = circle.id().value();
        List<CircleMemberEntity> existing = memberJpaRepository.findByCircleId(circleId);
        Set<UUID> existingUserIds = existing.stream()
                .map(CircleMemberEntity::getUserId)
                .collect(Collectors.toSet());
        Set<UUID> desiredUserIds = circle.members().stream()
                .map(m -> m.userId().value())
                .collect(Collectors.toSet());

        List<CircleMemberEntity> toDelete = existing.stream()
                .filter(e -> !desiredUserIds.contains(e.getUserId()))
                .toList();
        if (!toDelete.isEmpty()) {
            memberJpaRepository.deleteAll(toDelete);
        }

        for (Member member : circle.members()) {
            if (!existingUserIds.contains(member.userId().value())) {
                memberJpaRepository.save(toEntity(circleId, member));
            }
        }
    }

    @Override
    public Optional<Circle> findById(CircleId circleId) {
        return circleJpaRepository.findById(circleId.value()).map(this::toDomain);
    }

    private CircleMemberEntity toEntity(UUID circleId, Member member) {
        CircleMemberEntity entity = new CircleMemberEntity();
        entity.setCircleId(circleId);
        entity.setUserId(member.userId().value());
        entity.setRole(member.role().name());
        return entity;
    }

    private Circle toDomain(CircleEntity entity) {
        List<Member> members = memberJpaRepository.findByCircleId(entity.getId()).stream()
                .map(this::toMember)
                .toList();
        return Circle.reconstitute(
                CircleId.of(entity.getId()),
                entity.getName(),
                entity.getDescription(),
                InviteCode.of(entity.getInviteCode()),
                JoinPolicy.valueOf(entity.getJoinPolicy()),
                UserId.of(entity.getCreatedBy()),
                members);
    }

    private Member toMember(CircleMemberEntity entity) {
        return new Member(UserId.of(entity.getUserId()), MemberRole.valueOf(entity.getRole()));
    }
}

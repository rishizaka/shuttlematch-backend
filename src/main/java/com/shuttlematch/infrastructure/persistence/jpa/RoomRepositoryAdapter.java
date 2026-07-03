package com.shuttlematch.infrastructure.persistence.jpa;

import com.shuttlematch.domain.model.room.Participant;
import com.shuttlematch.domain.model.room.ParticipantId;
import com.shuttlematch.domain.model.room.ParticipantStatus;
import com.shuttlematch.domain.model.room.Room;
import com.shuttlematch.domain.model.room.RoomId;
import com.shuttlematch.domain.model.room.RoomStatus;
import com.shuttlematch.domain.model.user.UserId;
import com.shuttlematch.domain.repository.RoomRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

/**
 * {@link RoomRepository} の JPA 実装。
 * <p>
 * Room 集約と room_participants を 1 つの集約として扱うため、保存時に
 * 参加者コレクションの差分(追加・削除)を調整する。
 */
@Repository
public class RoomRepositoryAdapter implements RoomRepository {

    private final RoomJpaRepository roomJpaRepository;
    private final RoomParticipantJpaRepository participantJpaRepository;

    public RoomRepositoryAdapter(
            RoomJpaRepository roomJpaRepository,
            RoomParticipantJpaRepository participantJpaRepository) {
        this.roomJpaRepository = roomJpaRepository;
        this.participantJpaRepository = participantJpaRepository;
    }

    @Override
    public Room save(Room room) {
        RoomEntity entity = roomJpaRepository.findById(room.id().value())
                .orElseGet(RoomEntity::new);
        entity.setId(room.id().value());
        entity.setTitle(room.title());
        entity.setHeldAt(room.heldAt());
        entity.setLocation(room.location());
        entity.setCapacity(room.capacity());
        entity.setCourtCount(room.courtCount());
        entity.setStatus(room.status().name());
        entity.setCreatedBy(room.createdBy().value());
        roomJpaRepository.save(entity);

        reconcileParticipants(room);

        return findById(room.id()).orElseThrow();
    }

    @Override
    public List<Room> findByStatus(RoomStatus status) {
        return roomJpaRepository.findByStatusOrderByHeldAtAsc(status.name()).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public int countCreatedSince(UserId createdBy, OffsetDateTime since) {
        return (int) roomJpaRepository.countByCreatedByAndCreatedAtGreaterThanEqual(
                createdBy.value(), since);
    }

    private void reconcileParticipants(Room room) {
        UUID roomId = room.id().value();
        List<RoomParticipantEntity> existing = participantJpaRepository.findByRoomId(roomId);
        Map<UUID, RoomParticipantEntity> existingById = existing.stream()
                .collect(Collectors.toMap(RoomParticipantEntity::getId, e -> e));
        Set<UUID> desiredIds = room.participants().stream()
                .map(p -> p.id().value())
                .collect(Collectors.toSet());

        // 集約から取り除かれた参加者を削除
        List<RoomParticipantEntity> toDelete = existing.stream()
                .filter(e -> !desiredIds.contains(e.getId()))
                .toList();
        if (!toDelete.isEmpty()) {
            participantJpaRepository.deleteAll(toDelete);
        }

        // 新規は追加、既存は状態(早退など)・名前(ニックネーム)を更新
        for (Participant participant : room.participants()) {
            RoomParticipantEntity entity = existingById.get(participant.id().value());
            if (entity == null) {
                participantJpaRepository.save(toEntity(roomId, participant));
                continue;
            }
            boolean changed = false;
            if (!participant.status().name().equals(entity.getStatus())) {
                entity.setStatus(participant.status().name());
                changed = true;
            }
            if (!java.util.Objects.equals(participant.guestName(), entity.getGuestName())) {
                entity.setGuestName(participant.guestName());
                changed = true;
            }
            if (changed) {
                participantJpaRepository.save(entity);
            }
        }
    }

    @Override
    public Optional<Room> findById(RoomId roomId) {
        return roomJpaRepository.findById(roomId.value())
                .map(this::toDomain);
    }

    private RoomParticipantEntity toEntity(UUID roomId, Participant participant) {
        RoomParticipantEntity entity = new RoomParticipantEntity();
        entity.setId(participant.id().value());
        entity.setRoomId(roomId);
        entity.setUserId(participant.userId() == null ? null : participant.userId().value());
        entity.setGuestName(participant.guestName());
        entity.setStatus(participant.status().name());
        return entity;
    }

    private Room toDomain(RoomEntity entity) {
        List<Participant> participants = participantJpaRepository.findByRoomId(entity.getId()).stream()
                .map(this::toParticipant)
                .toList();
        return Room.reconstitute(
                RoomId.of(entity.getId()),
                entity.getTitle(),
                entity.getHeldAt(),
                entity.getLocation(),
                entity.getCapacity(),
                entity.getCourtCount(),
                RoomStatus.valueOf(entity.getStatus()),
                UserId.of(entity.getCreatedBy()),
                participants);
    }

    private Participant toParticipant(RoomParticipantEntity entity) {
        UserId userId = entity.getUserId() == null ? null : UserId.of(entity.getUserId());
        ParticipantStatus status = entity.getStatus() == null
                ? ParticipantStatus.ACTIVE
                : ParticipantStatus.valueOf(entity.getStatus());
        return Participant.reconstitute(
                ParticipantId.of(entity.getId()), userId, entity.getGuestName(), status);
    }
}

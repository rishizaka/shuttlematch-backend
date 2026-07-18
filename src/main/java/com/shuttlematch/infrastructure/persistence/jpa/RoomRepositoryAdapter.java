package com.shuttlematch.infrastructure.persistence.jpa;

import com.shuttlematch.domain.model.match.Pair;
import com.shuttlematch.domain.model.room.Participant;
import com.shuttlematch.domain.model.room.ParticipantId;
import com.shuttlematch.domain.model.room.ParticipantStatus;
import com.shuttlematch.domain.model.room.Room;
import com.shuttlematch.domain.model.room.RoomId;
import com.shuttlematch.domain.model.room.RoomStatus;
import com.shuttlematch.domain.model.user.UserId;
import com.shuttlematch.domain.repository.RoomRepository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
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
    private final RoomFixedPairJpaRepository fixedPairJpaRepository;

    public RoomRepositoryAdapter(
            RoomJpaRepository roomJpaRepository,
            RoomParticipantJpaRepository participantJpaRepository,
            RoomFixedPairJpaRepository fixedPairJpaRepository) {
        this.roomJpaRepository = roomJpaRepository;
        this.participantJpaRepository = participantJpaRepository;
        this.fixedPairJpaRepository = fixedPairJpaRepository;
    }

    @Override
    public Room save(Room room) {
        RoomEntity entity = roomJpaRepository.findById(room.id().value())
                .orElseGet(RoomEntity::new);
        entity.setId(room.id().value());
        entity.setShareCode(room.shareCode());
        entity.setTitle(room.title());
        entity.setHeldAt(room.heldAt());
        entity.setLocation(room.location());
        entity.setCapacity(room.capacity());
        entity.setCourtCount(room.courtCount());
        entity.setStatus(room.status().name());
        entity.setCreatedBy(room.createdBy().value());
        roomJpaRepository.save(entity);

        reconcileParticipants(room);
        reconcileFixedPairs(room);

        return findById(room.id()).orElseThrow();
    }

    @Override
    public Optional<Room> findByShareCode(String shareCode) {
        return roomJpaRepository.findByShareCode(shareCode).map(this::toDomain);
    }

    @Override
    public List<Room> findByStatus(RoomStatus status) {
        return roomJpaRepository.findByStatusOrderByHeldAtAsc(status.name()).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<Room> search(RoomStatus status, OffsetDateTime heldFrom, OffsetDateTime heldTo) {
        // null の条件は SQL に含めない(PostgreSQL は null パラメータの型を推論できないため、
        // (:p is null or ...) 方式ではなく Specification で動的に組み立てる)。
        List<Specification<RoomEntity>> conditions = new ArrayList<>();
        if (status != null) {
            conditions.add((root, query, cb) -> cb.equal(root.get("status"), status.name()));
        }
        if (heldFrom != null) {
            conditions.add((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("heldAt"), heldFrom));
        }
        if (heldTo != null) {
            conditions.add((root, query, cb) -> cb.lessThan(root.get("heldAt"), heldTo));
        }
        return roomJpaRepository
                .findAll(Specification.allOf(conditions), Sort.by(Sort.Direction.ASC, "heldAt"))
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public int countCreatedSince(UserId createdBy, OffsetDateTime since) {
        return (int) roomJpaRepository.countByCreatedByAndCreatedAtGreaterThanEqual(
                createdBy.value(), since);
    }

    @Override
    public List<Room> findNotClosedCreatedBefore(OffsetDateTime createdBefore) {
        return roomJpaRepository
                .findByStatusNotAndCreatedAtBefore(RoomStatus.CLOSED.name(), createdBefore)
                .stream()
                .map(this::toDomain)
                .toList();
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

    private void reconcileFixedPairs(Room room) {
        UUID roomId = room.id().value();
        List<RoomFixedPairEntity> existing = fixedPairJpaRepository.findByRoomId(roomId);
        Map<String, RoomFixedPairEntity> existingByKey = existing.stream()
                .collect(Collectors.toMap(
                        e -> pairKey(e.getParticipantA(), e.getParticipantB()), e -> e, (a, b) -> a));
        Set<String> desired = room.fixedPairs().stream()
                .map(p -> pairKey(p.player1().value(), p.player2().value()))
                .collect(Collectors.toSet());

        // 集約から取り除かれた固定ペアを削除
        List<RoomFixedPairEntity> toDelete = existing.stream()
                .filter(e -> !desired.contains(pairKey(e.getParticipantA(), e.getParticipantB())))
                .toList();
        if (!toDelete.isEmpty()) {
            fixedPairJpaRepository.deleteAll(toDelete);
        }

        // 新規の固定ペアを追加(既存はそのまま)
        for (Pair pair : room.fixedPairs()) {
            String key = pairKey(pair.player1().value(), pair.player2().value());
            if (!existingByKey.containsKey(key)) {
                fixedPairJpaRepository.save(toFixedPairEntity(roomId, pair));
            }
        }
    }

    private String pairKey(UUID a, UUID b) {
        return a + "_" + b;
    }

    private RoomFixedPairEntity toFixedPairEntity(UUID roomId, Pair pair) {
        RoomFixedPairEntity entity = new RoomFixedPairEntity();
        entity.setId(UUID.randomUUID());
        entity.setRoomId(roomId);
        // Pair は player1 < player2 (UUID昇順) に正規化済み
        entity.setParticipantA(pair.player1().value());
        entity.setParticipantB(pair.player2().value());
        return entity;
    }

    @Override
    public void deleteById(RoomId roomId) {
        // rooms 行を削除すると room_participants / room_fixed_pairs / match_schedules(→matches)
        // は DB の ON DELETE CASCADE で一緒に削除される。
        roomJpaRepository.deleteById(roomId.value());
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
        // 参加者番号は一覧の並び順で決まるため、参加順で安定的に取得する。
        List<Participant> participants = participantJpaRepository
                .findByRoomIdOrderByJoinOrderAsc(entity.getId()).stream()
                .map(this::toParticipant)
                .toList();
        List<Pair> fixedPairs = fixedPairJpaRepository.findByRoomId(entity.getId()).stream()
                .map(e -> new Pair(
                        ParticipantId.of(e.getParticipantA()),
                        ParticipantId.of(e.getParticipantB())))
                .toList();
        return Room.reconstitute(
                RoomId.of(entity.getId()),
                entity.getShareCode(),
                entity.getTitle(),
                entity.getHeldAt(),
                entity.getLocation(),
                entity.getCapacity(),
                entity.getCourtCount(),
                RoomStatus.valueOf(entity.getStatus()),
                UserId.of(entity.getCreatedBy()),
                participants,
                fixedPairs);
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

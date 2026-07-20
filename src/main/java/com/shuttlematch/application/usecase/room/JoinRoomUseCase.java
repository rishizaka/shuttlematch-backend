package com.shuttlematch.application.usecase.room;

import com.shuttlematch.application.ResourceNotFoundException;
import com.shuttlematch.domain.model.room.Participant;
import com.shuttlematch.domain.model.room.ParticipantId;
import com.shuttlematch.domain.model.room.Room;
import com.shuttlematch.domain.model.room.RoomId;
import com.shuttlematch.domain.repository.RoomRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 受付中ルームに参加者本人が自己参加するユースケース(名前必須)。
 * <p>
 * 番号(並び順)は永続化層の連番(join_order = bigserial)で原子的に採番されるため、
 * 同時参加でも番号の重複・欠番は起きない。生成後(GENERATED)の遅刻参加も可能で、
 * その場合は別途「未開始セットの再編成」で試合表へ反映する。
 */
@Service
public class JoinRoomUseCase {

    private final RoomRepository roomRepository;

    public JoinRoomUseCase(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    @Transactional
    public Result execute(RoomId roomId, String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("名前は必須です");
        }
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "セッションが見つかりません: " + roomId.value()));

        // addGuest でドメインの制約(終了済み不可・定員)を検証し新しい参加者を作る。
        // ただし集約全体は保存せず(同時参加で他者を消さないため)、この1名だけを追記する。
        Participant joined = room.addGuest(name.trim());
        roomRepository.insertParticipant(roomId, joined);

        Room saved = roomRepository.findById(roomId).orElseThrow();
        return new Result(saved, joined.id());
    }

    /** 保存後のルームと、参加した本人の participantId。 */
    public record Result(Room room, ParticipantId joinedId) {
    }
}

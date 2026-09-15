package com.shuttlematch.application.usecase.room;

import com.shuttlematch.application.ResourceNotFoundException;
import com.shuttlematch.domain.model.room.ParticipantId;
import com.shuttlematch.domain.model.room.Room;
import com.shuttlematch.domain.model.room.RoomId;
import com.shuttlematch.domain.model.room.RoomStatus;
import com.shuttlematch.domain.repository.RoomRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 「番号のまま(まだ誰も名乗っていない)」枠に、参加者本人が自動採番で参加するユースケース。
 * <p>
 * 人数を指定して作った(簡易作成)ルームは、参加者の枠が最初から番号だけで用意されている。
 * 1セット目が始まる前は、どの枠が自分かを選ばせる({@link RenameParticipantUseCase} を
 * 使った既存の「番号を選ぶ」導線)より、「参加する」を押すだけで一番若い空き番号が
 * 自動で割り当たるほうが分かりやすい。
 * <p>
 * 番号の割り当ては {@link RoomRepository#claimNextFreeSlot} が DB の行ロック
 * (FOR UPDATE SKIP LOCKED)で原子的に行うため、同時に複数人が押しても取り合いにならず、
 * それぞれ別の番号が割り振られる。
 */
@Service
public class ClaimNextParticipantUseCase {

    private final RoomRepository roomRepository;

    public ClaimNextParticipantUseCase(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    @Transactional
    public Result execute(RoomId roomId, String name) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "セッションが見つかりません: " + roomId.value()));
        if (room.status() == RoomStatus.CLOSED) {
            throw new IllegalStateException("終了したセッションには参加できません");
        }

        String trimmed = (name == null || name.isBlank()) ? "ゲスト" : name.trim();
        ParticipantId claimedId = roomRepository.claimNextFreeSlot(roomId, trimmed)
                .orElseThrow(() -> new IllegalStateException("参加できる空き番号がありません"));

        Room saved = roomRepository.findById(roomId).orElseThrow();
        return new Result(saved, claimedId);
    }

    /** 保存後のルームと、割り当てられた participantId。 */
    public record Result(Room room, ParticipantId claimedId) {
    }
}

package com.shuttlematch.application.usecase.room;

import com.shuttlematch.application.ResourceNotFoundException;
import com.shuttlematch.domain.model.room.Room;
import com.shuttlematch.domain.model.room.RoomId;
import com.shuttlematch.domain.model.room.RoomStatus;
import com.shuttlematch.domain.repository.MatchScheduleRepository;
import com.shuttlematch.domain.repository.RoomRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ルームを配下データごと完全に削除するユースケース。
 * <p>
 * 間違えて作成した場合などに、参加者・固定ペア・試合表を含めて破棄する。close(終了)と違い
 * 履歴には残らない。
 */
@Service
public class DeleteRoomUseCase {

    private final RoomRepository roomRepository;
    private final MatchScheduleRepository matchScheduleRepository;

    public DeleteRoomUseCase(
            RoomRepository roomRepository,
            MatchScheduleRepository matchScheduleRepository) {
        this.roomRepository = roomRepository;
        this.matchScheduleRepository = matchScheduleRepository;
    }

    @Transactional
    public void execute(RoomId roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "ルームが見つかりません: " + roomId.value()));
        // 終了したルームは消せない。過去の試合表は誰でも見られるよう一覧で roomId を
        // 公開しており、認可がまだ無いため、消せると誰にでも消せてしまう。
        // 間違えて作ったルームは終了する前に削除する(掃除が必要なら DB から直接消す)。
        if (room.status() == RoomStatus.CLOSED) {
            throw new IllegalStateException("終了したルームは削除できません");
        }
        // matches は room_participants を参照するがカスケード設定が無いため、先に試合表
        // (match_schedules→matches はカスケード)を消してから room を削除する。これで
        // room 削除時の participants カスケードが matches に阻まれない。
        matchScheduleRepository.deleteByRoomId(roomId);
        roomRepository.deleteById(roomId);
    }
}

package com.shuttlematch.application.usecase.room;

import com.shuttlematch.application.ResourceNotFoundException;
import com.shuttlematch.domain.model.room.ParticipantId;
import com.shuttlematch.domain.model.room.Room;
import com.shuttlematch.domain.model.room.RoomId;
import com.shuttlematch.domain.repository.RoomRepository;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 早退した参加者を在席(ACTIVE)に戻す。次回の再編成から編成対象に戻る。
 * <p>
 * executeMany() がある理由は {@link MarkParticipantLeftUseCase} の javadoc の
 * lost update と同じ(RoomRepositoryAdapter.save は集約を丸ごと差分保存するため、
 * 個別の execute() を並行で呼ぶと片方の変更が消えうる)。
 */
@Service
public class ReactivateParticipantUseCase {

    private final RoomRepository roomRepository;

    public ReactivateParticipantUseCase(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    @Transactional
    public Room execute(RoomId roomId, ParticipantId participantId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "セッションが見つかりません: " + roomId.value()));

        if (!room.reactivateParticipant(participantId)) {
            throw new ResourceNotFoundException("参加者が見つかりません: " + participantId.value());
        }
        return roomRepository.save(room);
    }

    /**
     * 複数の参加者をまとめて在席(ACTIVE)に戻す。1回の読み込み・保存で済ませる。
     * 途中の参加者が見つからなければ保存前に例外を投げるので、全員反映されるか
     * 1件も反映されないかのどちらかになる。
     */
    @Transactional
    public Room executeMany(RoomId roomId, List<ParticipantId> participantIds) {
        if (participantIds == null || participantIds.isEmpty()) {
            throw new IllegalArgumentException("参加者を指定してください");
        }
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "セッションが見つかりません: " + roomId.value()));

        for (ParticipantId participantId : participantIds) {
            if (!room.reactivateParticipant(participantId)) {
                throw new ResourceNotFoundException(
                        "参加者が見つかりません: " + participantId.value());
            }
        }
        return roomRepository.save(room);
    }
}

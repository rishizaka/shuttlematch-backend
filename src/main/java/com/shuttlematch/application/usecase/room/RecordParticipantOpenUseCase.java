package com.shuttlematch.application.usecase.room;

import com.shuttlematch.application.ResourceNotFoundException;
import com.shuttlematch.domain.model.room.RoomId;
import com.shuttlematch.domain.repository.RoomOpenRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 参加者の端末で試合表が開かれたことを記録するユースケース(KPI 用)。
 * <p>
 * フロントは「運営者ではない端末で、そのルームを初めて開いたとき」に1回だけ呼ぶ。
 * 認証は無い(このアプリ全体にまだ無い)ので水増しはできるが、計測値で運営には影響しない。
 */
@Service
public class RecordParticipantOpenUseCase {

    private final RoomOpenRepository roomOpenRepository;

    public RecordParticipantOpenUseCase(RoomOpenRepository roomOpenRepository) {
        this.roomOpenRepository = roomOpenRepository;
    }

    @Transactional
    public void execute(RoomId roomId) {
        if (!roomOpenRepository.recordParticipantOpen(roomId)) {
            throw new ResourceNotFoundException("セッションが見つかりません: " + roomId.value());
        }
    }
}

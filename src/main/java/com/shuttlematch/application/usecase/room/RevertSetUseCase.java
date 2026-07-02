package com.shuttlematch.application.usecase.room;

import com.shuttlematch.application.ResourceNotFoundException;
import com.shuttlematch.domain.model.match.MatchSchedule;
import com.shuttlematch.domain.model.room.RoomId;
import com.shuttlematch.domain.repository.MatchScheduleRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 進行中(最後に開始した)セットを開始前に戻すユースケース。開始時刻を消す。
 * 順番の整合性のため、戻せるのは「開始済みの最新セット(=進行中)」のみ。
 */
@Service
public class RevertSetUseCase {

    private final MatchScheduleRepository matchScheduleRepository;

    public RevertSetUseCase(MatchScheduleRepository matchScheduleRepository) {
        this.matchScheduleRepository = matchScheduleRepository;
    }

    @Transactional
    public MatchSchedule execute(RoomId roomId, int setNumber) {
        MatchSchedule schedule = matchScheduleRepository.findByRoomId(roomId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "試合スケジュールが見つかりません: room=" + roomId.value()));

        int maxStarted = schedule.nextStartableSetNumber() - 1;
        if (maxStarted <= 0) {
            throw new IllegalStateException("開始済みのセットがありません");
        }
        if (setNumber != maxStarted) {
            throw new IllegalStateException(
                    "開始前に戻せるのは進行中(第 " + maxStarted + " セット)のみです");
        }

        // startedAt を null にして開始前へ戻す。
        return matchScheduleRepository
                .startSet(roomId, setNumber, null)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "セットが見つかりません: room=" + roomId.value() + ", set=" + setNumber));
    }
}

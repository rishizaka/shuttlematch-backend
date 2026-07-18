package com.shuttlematch.application.usecase.room;

import com.shuttlematch.application.ResourceNotFoundException;
import com.shuttlematch.domain.model.match.MatchSchedule;
import com.shuttlematch.domain.model.room.Room;
import com.shuttlematch.domain.repository.MatchScheduleRepository;
import com.shuttlematch.domain.repository.RoomRepository;
import com.shuttlematch.domain.model.room.RoomId;
import com.shuttlematch.domain.service.MatchingDomainService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 既存の試合スケジュールにセットを追加するユースケース。
 * 最初に生成したセット数で足りなかった場合に、これまでの結果を保ったまま継ぎ足す。
 */
@Service
public class AddSetsUseCase {

    private final RoomRepository roomRepository;
    private final MatchScheduleRepository matchScheduleRepository;
    private final MatchingDomainService matchingDomainService;

    public AddSetsUseCase(
            RoomRepository roomRepository,
            MatchScheduleRepository matchScheduleRepository,
            MatchingDomainService matchingDomainService) {
        this.roomRepository = roomRepository;
        this.matchScheduleRepository = matchScheduleRepository;
        this.matchingDomainService = matchingDomainService;
    }

    @Transactional
    public MatchSchedule execute(RoomId roomId, int additionalSetCount) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "セッションが見つかりません: " + roomId.value()));

        MatchSchedule existing = matchScheduleRepository.findByRoomId(roomId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "試合スケジュールがまだ生成されていません: room=" + roomId.value()));

        int courtCount = room.courtCount() != null ? room.courtCount() : 1;
        MatchSchedule updated = matchingDomainService.addSets(
                existing, room.participantIds(), courtCount, additionalSetCount,
                room.fixedPairs());

        // 既存分の開始時刻もドメインオブジェクトに保持されているため、削除→保存で保たれる。
        matchScheduleRepository.deleteByRoomId(roomId);
        return matchScheduleRepository.save(updated);
    }
}

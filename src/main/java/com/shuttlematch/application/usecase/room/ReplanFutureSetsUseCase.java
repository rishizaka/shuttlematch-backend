package com.shuttlematch.application.usecase.room;

import com.shuttlematch.application.ResourceNotFoundException;
import com.shuttlematch.domain.model.match.MatchSchedule;
import com.shuttlematch.domain.model.room.Room;
import com.shuttlematch.domain.model.room.RoomId;
import com.shuttlematch.domain.repository.MatchScheduleRepository;
import com.shuttlematch.domain.repository.RoomRepository;
import com.shuttlematch.domain.service.MatchingDomainService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 参加者の増減(途中参加・早退)を受けて、未開始セットを現在の在席者で再編成するユースケース。
 * 開始済みセットは履歴として保持され、未開始セットのみが作り直される。
 */
@Service
public class ReplanFutureSetsUseCase {

    private final RoomRepository roomRepository;
    private final MatchScheduleRepository matchScheduleRepository;
    private final MatchingDomainService matchingDomainService;

    public ReplanFutureSetsUseCase(
            RoomRepository roomRepository,
            MatchScheduleRepository matchScheduleRepository,
            MatchingDomainService matchingDomainService) {
        this.roomRepository = roomRepository;
        this.matchScheduleRepository = matchScheduleRepository;
        this.matchingDomainService = matchingDomainService;
    }

    @Transactional
    public MatchSchedule execute(RoomId roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "セッションが見つかりません: " + roomId.value()));

        MatchSchedule existing = matchScheduleRepository.findByRoomId(roomId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "試合スケジュールがまだ生成されていません: room=" + roomId.value()));

        int courtCount = room.courtCount() != null ? room.courtCount() : 1;
        MatchSchedule updated = matchingDomainService.replanFuture(
                existing, room.activeParticipantIds(), courtCount, room.fixedPairs());

        matchScheduleRepository.deleteByRoomId(roomId);
        return matchScheduleRepository.save(updated);
    }
}

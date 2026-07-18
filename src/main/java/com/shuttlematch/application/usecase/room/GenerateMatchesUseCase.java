package com.shuttlematch.application.usecase.room;

import com.shuttlematch.application.ResourceNotFoundException;
import com.shuttlematch.domain.model.match.MatchSchedule;
import com.shuttlematch.domain.model.room.Room;
import com.shuttlematch.domain.repository.MatchScheduleRepository;
import com.shuttlematch.domain.repository.RoomRepository;
import com.shuttlematch.domain.service.MatchingDomainService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * セッションの参加者からダブルスの試合スケジュールを生成し永続化するユースケース。
 * 既にスケジュールがある場合は削除してから生成するため、再生成にも対応する。
 */
@Service
public class GenerateMatchesUseCase {

    private final RoomRepository roomRepository;
    private final MatchScheduleRepository matchScheduleRepository;
    private final MatchingDomainService matchingDomainService;

    public GenerateMatchesUseCase(
            RoomRepository roomRepository,
            MatchScheduleRepository matchScheduleRepository,
            MatchingDomainService matchingDomainService) {
        this.roomRepository = roomRepository;
        this.matchScheduleRepository = matchScheduleRepository;
        this.matchingDomainService = matchingDomainService;
    }

    @Transactional
    public MatchSchedule execute(GenerateMatchesCommand command) {
        Room room = roomRepository.findById(command.roomId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "セッションが見つかりません: " + command.roomId().value()));

        if (!room.status().allowsMatchGeneration()) {
            throw new IllegalStateException(
                    "このセッションは試合を生成できる状態ではありません: " + room.status());
        }

        int courtCount = room.courtCount() != null ? room.courtCount() : 1;
        MatchSchedule schedule = matchingDomainService.generate(
                room.id(), room.activeParticipantIds(), courtCount, command.matchCount(),
                room.fixedPairs());

        // 再生成に対応するため既存スケジュールを削除してから保存する
        matchScheduleRepository.deleteByRoomId(room.id());
        MatchSchedule saved = matchScheduleRepository.save(schedule);

        room.markGenerated();
        roomRepository.save(room);
        return saved;
    }
}

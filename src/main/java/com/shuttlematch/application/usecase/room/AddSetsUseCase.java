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
        if (!room.status().allowsMatchChanges()) {
            throw new IllegalStateException("終了したセッションの試合表は変更できません");
        }

        MatchSchedule existing = matchScheduleRepository.findByRoomId(roomId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "試合スケジュールがまだ生成されていません: room=" + roomId.value()));

        int courtCount = room.courtCount() != null ? room.courtCount() : 1;
        // 在席中(ACTIVE)の参加者だけを対象にする。participantIds() だと早退済み(LEFT)の
        // 人も含んでしまい、早退→再編成で除外したはずの人が追加セットに復活してしまう
        // (GenerateMatchesUseCase・QuickCreateRoomUseCase・ReplanFutureSetsUseCase は
        // いずれも activeParticipantIds() を使っており、ここだけ揃っていなかった)。
        MatchSchedule updated = matchingDomainService.addSets(
                existing, room.activeParticipantIds(), courtCount, additionalSetCount,
                room.fixedPairs());

        // 既存分の開始時刻もドメインオブジェクトに保持されているため、削除→保存で保たれる。
        matchScheduleRepository.deleteByRoomId(roomId);
        return matchScheduleRepository.save(updated);
    }
}

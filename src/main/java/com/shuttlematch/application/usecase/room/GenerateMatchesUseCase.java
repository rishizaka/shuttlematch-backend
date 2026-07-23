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

    /** ダブルス1試合の人数。コート数 × これが試合表生成の最低人数。 */
    private static final int PLAYERS_PER_MATCH = 4;

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

        // 在席がコート数 × 4 に満たない分は、番号だけのゲスト枠で埋めてから生成する。
        // (募集して受付するとき、集まりが少なくてもゲストとして開催できるようにする。
        //  追加した枠は「番号だけの空き」なので、遅刻者が後から名前を付けて入れる)
        int required = courtCount * PLAYERS_PER_MATCH;
        int shortfall = required - room.activeParticipantIds().size();
        for (int i = 0; i < shortfall; i++) {
            // 表示番号(参加者の並び順)と一致するよう、末尾の番号を名前にする。
            room.addGuest(String.valueOf(room.participants().size() + 1));
        }

        MatchSchedule schedule = matchingDomainService.generate(
                room.id(), room.activeParticipantIds(), courtCount, command.matchCount(),
                room.fixedPairs());

        // 補充したゲストを含む参加者を先に永続化する。
        // スケジュール(試合)は参加者IDを参照するため、先に room を保存しないと
        // 未保存のゲストを参照して外部キー制約に違反する。
        room.markGenerated();
        roomRepository.save(room);

        // 再生成に対応するため既存スケジュールを削除してから保存する
        matchScheduleRepository.deleteByRoomId(room.id());
        return matchScheduleRepository.save(schedule);
    }
}

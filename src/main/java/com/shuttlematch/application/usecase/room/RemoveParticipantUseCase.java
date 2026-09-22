package com.shuttlematch.application.usecase.room;

import com.shuttlematch.application.ResourceNotFoundException;
import com.shuttlematch.domain.model.match.Match;
import com.shuttlematch.domain.model.match.MatchSchedule;
import com.shuttlematch.domain.model.room.Participant;
import com.shuttlematch.domain.model.room.ParticipantId;
import com.shuttlematch.domain.model.room.Room;
import com.shuttlematch.domain.model.room.RoomId;
import com.shuttlematch.domain.model.room.RoomStatus;
import com.shuttlematch.domain.repository.MatchScheduleRepository;
import com.shuttlematch.domain.repository.RoomRepository;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * セッションから参加者を削除する(参加者数そのものを減らす)ユースケース。
 * <p>
 * 早退(status=LEFT)とは違い、削除は行そのものを消すので {@code participantCount}
 * (一覧カードの「参加 N」等)からも減る。ただし試合生成後は「番号のまま(誰も参加
 * していない)」枠も含め全員が生成直後から全セットの試合データに組み込まれるため、
 * 無条件に削除できるわけではない:
 * <ul>
 *   <li><b>生成前(PREPARING/OPEN)</b>: 誰でも削除できる(従来通り)。</li>
 *   <li><b>生成後(GENERATED)、1セットも開始していない</b>: <b>一番後ろの番号</b>の
 *       参加者だけ削除できる。早退と同じ手順でいったん編成対象から外して試合表を
 *       組み直し(={@link ReplanFutureSetsUseCase})、参照が無くなってから行を削除する。
 *       途中の番号を削除すると後続の番号が繰り上がってしまう(既に「7番」として
 *       announcementやツールチップに登場した人が急に「6番」になる)ため、末尾限定にしてある。</li>
 *   <li><b>1セットでも開始した後、または終了済み</b>: 削除できない。早退を使う。</li>
 * </ul>
 */
@Service
public class RemoveParticipantUseCase {

    private final RoomRepository roomRepository;
    private final MatchScheduleRepository matchScheduleRepository;
    private final ReplanFutureSetsUseCase replanFutureSetsUseCase;

    public RemoveParticipantUseCase(
            RoomRepository roomRepository,
            MatchScheduleRepository matchScheduleRepository,
            ReplanFutureSetsUseCase replanFutureSetsUseCase) {
        this.roomRepository = roomRepository;
        this.matchScheduleRepository = matchScheduleRepository;
        this.replanFutureSetsUseCase = replanFutureSetsUseCase;
    }

    @Transactional
    public void execute(RoomId roomId, ParticipantId participantId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "セッションが見つかりません: " + roomId.value()));

        if (room.status() == RoomStatus.PREPARING || room.status() == RoomStatus.OPEN) {
            // 生成前は誰でも削除できる(従来通り。試合データがまだ無いので安全)。
            if (!room.removeParticipant(participantId)) {
                throw new ResourceNotFoundException(
                        "参加者が見つかりません: " + participantId.value());
            }
            roomRepository.save(room);
            return;
        }

        if (room.status() == RoomStatus.CLOSED) {
            throw new IllegalStateException("終了したセッションの参加者は削除できません");
        }

        // GENERATED: 存在確認 → 末尾か → まだどのセットも開始していないか、の順で検証する。
        List<Participant> participants = room.participants();
        boolean exists = participants.stream().anyMatch(p -> p.id().equals(participantId));
        if (!exists) {
            throw new ResourceNotFoundException(
                    "参加者が見つかりません: " + participantId.value());
        }
        boolean isLast = participants.get(participants.size() - 1).id().equals(participantId);
        if (!isLast) {
            throw new IllegalStateException(
                    "削除できるのは一番後ろの番号の参加者だけです(それ以外は早退を使ってください)");
        }
        Optional<MatchSchedule> schedule = matchScheduleRepository.findByRoomId(roomId);
        boolean anySetStarted = schedule.isPresent()
                && schedule.get().matches().stream().anyMatch(Match::isStarted);
        if (anySetStarted) {
            throw new IllegalStateException(
                    "1セットでも開始した後は削除できません(早退を使ってください)");
        }

        // 早退と同じ手順でいったん編成対象から外す(在席のままだと、この参加者を参照する
        // 試合データがDBに残ったまま行を削除しようとして外部キー制約に反してしまう)。
        if (!room.markParticipantLeft(participantId)) {
            throw new ResourceNotFoundException(
                    "参加者が見つかりません: " + participantId.value());
        }
        roomRepository.save(room);

        // 試合表があれば、この参加者を含まない形に組み直す。まだどのセットも開始していない
        // ことを検証済みなので、組み直し後は既存の試合データからこの参加者への参照が無くなる。
        if (schedule.isPresent()) {
            replanFutureSetsUseCase.execute(roomId);
        }

        // ここまでで試合データからの参照が無くなっているので、行を安全に削除できる。
        Room reloaded = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "セッションが見つかりません: " + roomId.value()));
        reloaded.removeParticipant(participantId);
        roomRepository.save(reloaded);
    }
}

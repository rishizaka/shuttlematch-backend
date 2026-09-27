package com.shuttlematch.application.usecase.room;

import com.shuttlematch.application.ForbiddenOperationException;
import com.shuttlematch.application.ResourceNotFoundException;
import com.shuttlematch.domain.model.room.Room;
import com.shuttlematch.domain.model.room.RoomId;
import com.shuttlematch.domain.repository.MatchScheduleRepository;
import com.shuttlematch.domain.repository.RoomRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ルームを配下データごと完全に削除するユースケース。
 * <p>
 * 間違えて作成した場合や、終わった練習会の記録を消したい場合に、参加者・固定ペア・試合表を
 * 含めて破棄する。close(終了)と違い履歴には残らない。
 * <p>
 * <b>共有コードを知っている人だけが削除できる。</b>この API 群にはまだ認証が無く、
 * 終了したルームは roomId を一覧で公開している({@code PublicRoomResponse})ため、
 * roomId だけで消せると一覧を取得して順に叩くだけで過去の記録を全消しできてしまう。
 * 共有コードは一覧に載せていないので、リンクを渡された人だけが持つ。
 * セット開始やルーム終了と同じ「リンクを知っている人はできる」水準に揃えている。
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

    /**
     * ルームを削除する。
     *
     * @param shareCode そのルームの共有コード。一致しなければ削除しない
     */
    @Transactional
    public void execute(RoomId roomId, String shareCode) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "ランダム表が見つかりません: " + roomId.value()));
        if (shareCode == null || !room.shareCode().equals(shareCode)) {
            throw new ForbiddenOperationException("共有コードが一致しないため削除できません");
        }
        // matches は room_participants を参照するがカスケード設定が無いため、先に試合表
        // (match_schedules→matches はカスケード)を消してから room を削除する。これで
        // room 削除時の participants カスケードが matches に阻まれない。
        matchScheduleRepository.deleteByRoomId(roomId);
        roomRepository.deleteById(roomId);
    }
}

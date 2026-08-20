package com.shuttlematch.application.usecase.room;

import com.shuttlematch.domain.model.room.Room;
import com.shuttlematch.domain.repository.RoomRepository;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 作成から一定時間が経過したルームを一括で終了する(履歴に送る)ユースケース。
 * スケジューラから定期的に呼ばれ、終了し忘れたルームを自動で過去のものにする。
 */
@Service
public class CloseExpiredRoomsUseCase {

    /**
     * 作成からこの時間が経過したルームを自動で終了する。
     * 前日夜に作ったルームで翌日の練習会をやる、当日の朝に作って夜まで続く、
     * といった使われ方があるため、当日中(12時間)では短い。
     */
    public static final Duration ROOM_TTL = Duration.ofHours(36);

    private final RoomRepository roomRepository;

    public CloseExpiredRoomsUseCase(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    /** 期限切れルームをすべて終了し、終了した件数を返す。 */
    @Transactional
    public int execute(OffsetDateTime now) {
        List<Room> expired = roomRepository.findNotClosedCreatedBefore(now.minus(ROOM_TTL));
        for (Room room : expired) {
            room.close();
            roomRepository.save(room);
        }
        return expired.size();
    }
}

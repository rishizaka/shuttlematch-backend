package com.shuttlematch.application.usecase.room;

import com.shuttlematch.domain.model.room.Room;
import com.shuttlematch.domain.model.user.UserId;
import com.shuttlematch.domain.repository.RoomRepository;

import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * 1ユーザーが1日に作成できるルーム数の制限。CreateRoomUseCase / QuickCreateRoomUseCase から共通で使う。
 * 「1日」は日本時間の暦日で区切る。
 */
final class RoomCreationLimit {

    private static final ZoneId JST = ZoneId.of("Asia/Tokyo");

    private RoomCreationLimit() {
    }

    static void check(RoomRepository roomRepository, UserId createdBy) {
        OffsetDateTime startOfToday = java.time.LocalDate.now(JST).atStartOfDay(JST).toOffsetDateTime();
        int createdToday = roomRepository.countCreatedSince(createdBy, startOfToday);
        if (createdToday >= Room.MAX_ROOMS_PER_USER_PER_DAY) {
            throw new IllegalStateException(
                    "1日に作成できるランダム表は " + Room.MAX_ROOMS_PER_USER_PER_DAY + " 件までです");
        }
    }
}

package com.shuttlematch.presentation.api.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.shuttlematch.domain.model.room.RoomId;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PublicRoomIdTest {

    // フロントエンドの public-room-id.test.ts と同じ値を持つ。どちらかの式が変わると
    // 照合が全て外れ、TOP から自分のルームに入れなくなるので両側で固定しておく。
    private static final String ROOM_ID = "3fa85f64-5717-4562-b3fc-2c963f66afa6";
    private static final String PUBLIC_ID = "c7aa09cd25da8b6a";

    @Test
    @DisplayName("roomId の SHA-256 先頭16桁を返す(フロントと同じ式)")
    void computesSha256Prefix() {
        assertThat(PublicRoomId.of(RoomId.of(UUID.fromString(ROOM_ID)))).isEqualTo(PUBLIC_ID);
    }

    @Test
    @DisplayName("別のルームなら別の公開IDになる")
    void differsPerRoom() {
        assertThat(PublicRoomId.of(RoomId.newId()))
                .isNotEqualTo(PublicRoomId.of(RoomId.newId()));
    }
}

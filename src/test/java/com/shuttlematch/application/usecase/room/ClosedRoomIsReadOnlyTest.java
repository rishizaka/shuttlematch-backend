package com.shuttlematch.application.usecase.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shuttlematch.domain.model.room.Participant;
import com.shuttlematch.domain.model.room.Room;
import com.shuttlematch.domain.model.room.RoomStatus;
import com.shuttlematch.domain.model.user.UserId;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 終了したルームが「読むだけ」になっていることの確認。
 * <p>
 * 一覧は終了したルームの roomId を公開している(過去の試合表を誰でも見られるようにするため)。
 * この API 群にはまだ認証が無いので、roomId が知られても壊せないことが前提になる。
 * ここが崩れると、過去の記録を誰でも消したり作り直したりできてしまう。
 */
class ClosedRoomIsReadOnlyTest {

    @Test
    @DisplayName("終了したルームは参加者を削除できない")
    void closedRoomDisallowsParticipantRemoval() {
        // removeParticipant() のステータス制約(ensureNotClosed)を直接確認する。
        // 「生成前のみ / 末尾のみ」等それ以外の安全性は RemoveParticipantUseCase の責務で、
        // RemoveParticipantUseCaseTest 側で確認している。
        Room room = Room.create(
                "練習会", OffsetDateTime.parse("2026-07-01T18:00:00+09:00"),
                null, null, UserId.of(UUID.randomUUID()));
        Participant guest = room.addGuest("ゲスト");
        room.close();

        assertThatThrownBy(() -> room.removeParticipant(guest.id()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("終了したルームは試合表を生成し直せない")
    void closedRoomDisallowsMatchGeneration() {
        assertThat(RoomStatus.CLOSED.allowsMatchGeneration()).isFalse();
    }

    @Test
    @DisplayName("終了したルームは試合表を変更できない(セット追加・開始・巻き戻し・再編成)")
    void closedRoomDisallowsMatchChanges() {
        assertThat(RoomStatus.CLOSED.allowsMatchChanges()).isFalse();
        // 終了前は変更できる。
        assertThat(RoomStatus.OPEN.allowsMatchChanges()).isTrue();
        assertThat(RoomStatus.GENERATED.allowsMatchChanges()).isTrue();
        assertThat(RoomStatus.PREPARING.allowsMatchChanges()).isTrue();
    }

    @Test
    @DisplayName("終了したルームは二重に終了できない")
    void closedRoomCannotBeClosedAgain() {
        var room = com.shuttlematch.domain.model.room.Room.create(
                "練習会", java.time.OffsetDateTime.parse("2026-07-01T18:00:00+09:00"),
                null, null,
                com.shuttlematch.domain.model.user.UserId.of(java.util.UUID.randomUUID()));
        room.close();

        assertThatThrownBy(room::close).isInstanceOf(IllegalStateException.class);
    }
}

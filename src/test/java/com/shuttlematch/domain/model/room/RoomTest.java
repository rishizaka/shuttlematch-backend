package com.shuttlematch.domain.model.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shuttlematch.domain.model.user.UserId;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RoomTest {

    private Room newSession(Integer capacity) {
        return Room.create(
                "練習会", OffsetDateTime.now(),
                "体育館", capacity, UserId.of(UUID.randomUUID()));
    }

    @Test
    @DisplayName("作成直後は OPEN かつ参加者0")
    void createdSessionIsOpenAndEmpty() {
        Room room = newSession(null);
        assertThat(room.status()).isEqualTo(RoomStatus.OPEN);
        assertThat(room.participants()).isEmpty();
    }

    @Test
    @DisplayName("タイトル未入力は作成できない")
    void cannotCreateWithoutTitle() {
        assertThatThrownBy(() -> Room.create(
                "  ", OffsetDateTime.now(),
                null, null, UserId.of(UUID.randomUUID())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("登録ユーザーとゲストを追加できる")
    void addsUserAndGuest() {
        Room room = newSession(null);
        UserId user = UserId.of(UUID.randomUUID());
        Participant added = room.addUser(user);
        room.addGuest("ゲストA");

        assertThat(room.participants()).hasSize(2);
        assertThat(added.userId()).isEqualTo(user);
    }

    @Test
    @DisplayName("同じユーザーの重複参加はできない")
    void rejectsDuplicateUser() {
        Room room = newSession(null);
        UserId user = UserId.of(UUID.randomUUID());
        room.addUser(user);
        assertThatThrownBy(() -> room.addUser(user))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("定員に達すると追加できない")
    void rejectsWhenCapacityReached() {
        Room room = newSession(4);
        for (int i = 0; i < 4; i++) {
            room.addGuest("ゲスト" + i);
        }
        assertThatThrownBy(() -> room.addGuest("超過"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("参加者を削除できる。存在しなければ false")
    void removesParticipant() {
        Room room = newSession(null);
        Participant p = room.addGuest("ゲスト");
        assertThat(room.removeParticipant(p.id())).isTrue();
        assertThat(room.removeParticipant(p.id())).isFalse();
        assertThat(room.participants()).isEmpty();
    }

    @Test
    @DisplayName("生成済みでも参加者を追加できる(途中参加)が、削除はできない(早退を使う)")
    void allowsAddButNotRemoveAfterGenerated() {
        Room room = newSession(null);
        Participant existing = room.addGuest("ゲスト");
        room.markGenerated();
        assertThat(room.status()).isEqualTo(RoomStatus.GENERATED);

        // 途中参加は可能
        Participant late = room.addGuest("遅参");
        assertThat(room.participants()).hasSize(2);
        // 生成後の削除は不可
        assertThatThrownBy(() -> room.removeParticipant(late.id()))
                .isInstanceOf(IllegalStateException.class);
        // 早退マークは可能。履歴は残る(件数は変わらない)。
        assertThat(room.markParticipantLeft(existing.id())).isTrue();
        assertThat(room.participants()).hasSize(2);
        assertThat(room.activeParticipantIds()).containsExactly(late.id());
    }

    @Test
    @DisplayName("close で終了済みに遷移する。二重終了は例外")
    void closesSession() {
        Room room = newSession(null);
        room.close();
        assertThat(room.status()).isEqualTo(RoomStatus.CLOSED);
        assertThatThrownBy(room::close).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("終了済みセッションには参加者を追加できない")
    void cannotAddAfterClosed() {
        Room closed = Room.reconstitute(
                RoomId.newId(), "testcode", "終了", OffsetDateTime.now(),
                null, null, null, RoomStatus.CLOSED,                 UserId.of(UUID.randomUUID()), List.of());
        assertThatThrownBy(() -> closed.addGuest("追加"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("終了済みセッションは生成済みに遷移できない")
    void closedCannotBeGenerated() {
        Room closed = Room.reconstitute(
                RoomId.newId(), "testcode", "終了", OffsetDateTime.now(),
                null, null, null, RoomStatus.CLOSED,                 UserId.of(UUID.randomUUID()), List.of());
        assertThatThrownBy(closed::markGenerated)
                .isInstanceOf(IllegalStateException.class);
    }
}

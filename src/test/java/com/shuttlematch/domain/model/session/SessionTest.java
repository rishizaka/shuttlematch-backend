package com.shuttlematch.domain.model.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shuttlematch.domain.model.circle.CircleId;
import com.shuttlematch.domain.model.user.UserId;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SessionTest {

    private Session newSession(Integer capacity) {
        return Session.create(
                CircleId.of(UUID.randomUUID()), "練習会", OffsetDateTime.now(),
                "体育館", capacity, UserId.of(UUID.randomUUID()));
    }

    @Test
    @DisplayName("作成直後は OPEN かつ参加者0")
    void createdSessionIsOpenAndEmpty() {
        Session session = newSession(null);
        assertThat(session.status()).isEqualTo(SessionStatus.OPEN);
        assertThat(session.participants()).isEmpty();
    }

    @Test
    @DisplayName("タイトル未入力は作成できない")
    void cannotCreateWithoutTitle() {
        assertThatThrownBy(() -> Session.create(
                CircleId.of(UUID.randomUUID()), "  ", OffsetDateTime.now(),
                null, null, UserId.of(UUID.randomUUID())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("登録ユーザーとゲストを追加できる")
    void addsUserAndGuest() {
        Session session = newSession(null);
        UserId user = UserId.of(UUID.randomUUID());
        Participant added = session.addUser(user);
        session.addGuest("ゲストA");

        assertThat(session.participants()).hasSize(2);
        assertThat(added.userId()).isEqualTo(user);
    }

    @Test
    @DisplayName("同じユーザーの重複参加はできない")
    void rejectsDuplicateUser() {
        Session session = newSession(null);
        UserId user = UserId.of(UUID.randomUUID());
        session.addUser(user);
        assertThatThrownBy(() -> session.addUser(user))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("定員に達すると追加できない")
    void rejectsWhenCapacityReached() {
        Session session = newSession(4);
        for (int i = 0; i < 4; i++) {
            session.addGuest("ゲスト" + i);
        }
        assertThatThrownBy(() -> session.addGuest("超過"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("参加者を削除できる。存在しなければ false")
    void removesParticipant() {
        Session session = newSession(null);
        Participant p = session.addGuest("ゲスト");
        assertThat(session.removeParticipant(p.id())).isTrue();
        assertThat(session.removeParticipant(p.id())).isFalse();
        assertThat(session.participants()).isEmpty();
    }

    @Test
    @DisplayName("生成済みになると参加者を変更できない")
    void cannotModifyAfterGenerated() {
        Session session = newSession(null);
        session.addGuest("ゲスト");
        session.markGenerated();
        assertThat(session.status()).isEqualTo(SessionStatus.GENERATED);
        assertThatThrownBy(() -> session.addGuest("追加"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("終了済みセッションは生成済みに遷移できない")
    void closedCannotBeGenerated() {
        Session closed = Session.reconstitute(
                SessionId.newId(), CircleId.of(UUID.randomUUID()), "終了", OffsetDateTime.now(),
                null, null, null, SessionStatus.CLOSED, SessionVisibility.PUBLIC,
                UserId.of(UUID.randomUUID()), List.of());
        assertThatThrownBy(closed::markGenerated)
                .isInstanceOf(IllegalStateException.class);
    }
}

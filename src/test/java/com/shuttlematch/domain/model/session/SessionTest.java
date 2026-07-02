package com.shuttlematch.domain.model.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shuttlematch.domain.model.user.UserId;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SessionTest {

    private Session newSession(Integer capacity) {
        return Session.create(
                "練習会", OffsetDateTime.now(),
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
                "  ", OffsetDateTime.now(),
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
    @DisplayName("生成済みでも参加者を追加できる(途中参加)が、削除はできない(早退を使う)")
    void allowsAddButNotRemoveAfterGenerated() {
        Session session = newSession(null);
        Participant existing = session.addGuest("ゲスト");
        session.markGenerated();
        assertThat(session.status()).isEqualTo(SessionStatus.GENERATED);

        // 途中参加は可能
        Participant late = session.addGuest("遅参");
        assertThat(session.participants()).hasSize(2);
        // 生成後の削除は不可
        assertThatThrownBy(() -> session.removeParticipant(late.id()))
                .isInstanceOf(IllegalStateException.class);
        // 早退マークは可能。履歴は残る(件数は変わらない)。
        assertThat(session.markParticipantLeft(existing.id())).isTrue();
        assertThat(session.participants()).hasSize(2);
        assertThat(session.activeParticipantIds()).containsExactly(late.id());
    }

    @Test
    @DisplayName("close で終了済みに遷移する。二重終了は例外")
    void closesSession() {
        Session session = newSession(null);
        session.close();
        assertThat(session.status()).isEqualTo(SessionStatus.CLOSED);
        assertThatThrownBy(session::close).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("終了済みセッションには参加者を追加できない")
    void cannotAddAfterClosed() {
        Session closed = Session.reconstitute(
                SessionId.newId(), "終了", OffsetDateTime.now(),
                null, null, null, SessionStatus.CLOSED,                 UserId.of(UUID.randomUUID()), List.of());
        assertThatThrownBy(() -> closed.addGuest("追加"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("終了済みセッションは生成済みに遷移できない")
    void closedCannotBeGenerated() {
        Session closed = Session.reconstitute(
                SessionId.newId(), "終了", OffsetDateTime.now(),
                null, null, null, SessionStatus.CLOSED,                 UserId.of(UUID.randomUUID()), List.of());
        assertThatThrownBy(closed::markGenerated)
                .isInstanceOf(IllegalStateException.class);
    }
}

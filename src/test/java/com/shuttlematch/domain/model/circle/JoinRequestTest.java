package com.shuttlematch.domain.model.circle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shuttlematch.domain.model.user.UserId;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JoinRequestTest {

    private JoinRequest pending() {
        return JoinRequest.apply(CircleId.of(UUID.randomUUID()), UserId.of(UUID.randomUUID()));
    }

    @Test
    @DisplayName("申請は PENDING で開始し requestedAt を持つ")
    void appliesAsPending() {
        JoinRequest request = pending();
        assertThat(request.status()).isEqualTo(JoinRequestStatus.PENDING);
        assertThat(request.isPending()).isTrue();
        assertThat(request.requestedAt()).isNotNull();
        assertThat(request.decidedAt()).isNull();
    }

    @Test
    @DisplayName("承認すると APPROVED になり decidedAt が入る")
    void approves() {
        JoinRequest request = pending();
        request.approve();
        assertThat(request.status()).isEqualTo(JoinRequestStatus.APPROVED);
        assertThat(request.decidedAt()).isNotNull();
    }

    @Test
    @DisplayName("却下すると REJECTED になる")
    void rejects() {
        JoinRequest request = pending();
        request.reject();
        assertThat(request.status()).isEqualTo(JoinRequestStatus.REJECTED);
    }

    @Test
    @DisplayName("処理済みの申請は再度承認・却下できない")
    void cannotDecideTwice() {
        JoinRequest request = pending();
        request.approve();
        assertThatThrownBy(request::approve).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(request::reject).isInstanceOf(IllegalStateException.class);
    }
}

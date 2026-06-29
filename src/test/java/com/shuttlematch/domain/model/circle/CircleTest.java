package com.shuttlematch.domain.model.circle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shuttlematch.domain.model.user.UserId;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CircleTest {

    @Test
    @DisplayName("作成時に作成者が ORGANIZER として登録され、招待コードが発行される")
    void createRegistersCreatorAsOrganizer() {
        UserId creator = UserId.of(UUID.randomUUID());
        Circle circle = Circle.create("テニス部", "説明", JoinPolicy.OPEN, creator);

        assertThat(circle.members()).hasSize(1);
        assertThat(circle.members().get(0).userId()).isEqualTo(creator);
        assertThat(circle.members().get(0).isOrganizer()).isTrue();
        assertThat(circle.inviteCode().value()).isNotBlank();
    }

    @Test
    @DisplayName("名前が空だと作成できない")
    void cannotCreateWithoutName() {
        assertThatThrownBy(() -> Circle.create("  ", null, JoinPolicy.OPEN, UserId.of(UUID.randomUUID())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("メンバーを追加できる")
    void addsMember() {
        Circle circle = Circle.create("部活", null, JoinPolicy.OPEN, UserId.of(UUID.randomUUID()));
        UserId newMember = UserId.of(UUID.randomUUID());
        circle.addMember(newMember, MemberRole.PLAYER);
        assertThat(circle.members()).hasSize(2);
    }

    @Test
    @DisplayName("同じユーザーの重複メンバー追加はできない")
    void rejectsDuplicateMember() {
        UserId creator = UserId.of(UUID.randomUUID());
        Circle circle = Circle.create("部活", null, JoinPolicy.OPEN, creator);
        assertThatThrownBy(() -> circle.addMember(creator, MemberRole.PLAYER))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

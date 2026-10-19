package com.shuttlematch.domain.model.room;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.shuttlematch.domain.model.user.UserId;

class OrganizerTokenTest {

    @Test
    @DisplayName("発行したトークンは毎回異なる")
    void generatesUniqueTokens() {
        var tokens = IntStream.range(0, 1000)
                .mapToObj(i -> OrganizerToken.generate())
                .collect(Collectors.toSet());
        assertThat(tokens).hasSize(1000);
    }

    @Test
    @DisplayName("ハッシュは16進64文字で、同じ平文からは同じ値になる")
    void hashIsStableHex() {
        String token = OrganizerToken.generate();
        assertThat(OrganizerToken.hash(token))
                .hasSize(64)
                .matches("[0-9a-f]{64}")
                .isEqualTo(OrganizerToken.hash(token));
    }

    @Test
    @DisplayName("一致・不一致・null・空文字を正しく判定する")
    void matchesOnlyTheOriginalToken() {
        String token = OrganizerToken.generate();
        String hash = OrganizerToken.hash(token);

        assertThat(OrganizerToken.matches(token, hash)).isTrue();
        assertThat(OrganizerToken.matches(OrganizerToken.generate(), hash)).isFalse();
        assertThat(OrganizerToken.matches(null, hash)).isFalse();
        assertThat(OrganizerToken.matches("", hash)).isFalse();
        assertThat(OrganizerToken.matches("  ", hash)).isFalse();
        // ハッシュ未設定のルームは誰も運営者と認めない(fail-closed)
        assertThat(OrganizerToken.matches(token, null)).isFalse();
    }

    @Test
    @DisplayName("作成したルームは発行トークンの持ち主だけを運営者と認める")
    void createdRoomRecognizesItsOrganizer() {
        Room room = Room.create(
                "練習会", OffsetDateTime.now(), null, null, UserId.of(UUID.randomUUID()));

        String issued = room.issuedOrganizerToken();
        assertThat(issued).isNotBlank();
        assertThat(room.isOrganizer(issued)).isTrue();
        assertThat(room.isOrganizer(OrganizerToken.generate())).isFalse();
        assertThat(room.isOrganizer(null)).isFalse();
    }

    @Test
    @DisplayName("永続化層から復元したルームは平文を持たず、ハッシュ照合で運営者を判定する")
    void reconstitutedRoomKeepsHashButNotPlaintext() {
        Room created = Room.create(
                "練習会", OffsetDateTime.now(), null, null, UserId.of(UUID.randomUUID()));
        String issued = created.issuedOrganizerToken();

        Room restored = Room.reconstitute(
                created.id(), created.shareCode(), created.title(), created.heldAt(),
                created.location(), created.capacity(), created.courtCount(), created.status(),
                created.createdBy(), List.of(), List.of(), created.organizerTokenHash());

        assertThat(restored.issuedOrganizerToken()).isNull();
        assertThat(restored.isOrganizer(issued)).isTrue();
        assertThat(restored.isOrganizer(OrganizerToken.generate())).isFalse();
    }

    @Test
    @DisplayName("ハッシュを持たないルーム(トークン導入前に作ったもの)は従来どおり誰でも運営操作できる")
    void legacyRoomWithoutHashIsOpen() {
        Room created = Room.create(
                "練習会", OffsetDateTime.now(), null, null, UserId.of(UUID.randomUUID()));

        Room legacy = Room.reconstitute(
                created.id(), created.shareCode(), created.title(), created.heldAt(),
                created.location(), created.capacity(), created.courtCount(), created.status(),
                created.createdBy(), List.of(), List.of(), null);

        assertThat(legacy.isOrganizer(null)).isTrue();
        assertThat(legacy.isOrganizer("anything")).isTrue();
    }

    @Test
    @DisplayName("ハッシュを持つルームは、トークンが無ければ運営者と認めない")
    void protectedRoomRejectsMissingToken() {
        Room created = Room.create(
                "練習会", OffsetDateTime.now(), null, null, UserId.of(UUID.randomUUID()));

        Room restored = Room.reconstitute(
                created.id(), created.shareCode(), created.title(), created.heldAt(),
                created.location(), created.capacity(), created.courtCount(), created.status(),
                created.createdBy(), List.of(), List.of(), created.organizerTokenHash());

        assertThat(restored.isOrganizer(null)).isFalse();
        assertThat(restored.isOrganizer(OrganizerToken.generate())).isFalse();
        assertThat(restored.isOrganizer(created.issuedOrganizerToken())).isTrue();
    }
}

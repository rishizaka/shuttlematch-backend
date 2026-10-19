package com.shuttlematch.application.usecase.room;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.shuttlematch.application.OrganizerForbiddenException;
import com.shuttlematch.application.ResourceNotFoundException;
import com.shuttlematch.domain.model.room.OrganizerToken;
import com.shuttlematch.domain.model.room.Room;
import com.shuttlematch.domain.model.room.RoomId;
import com.shuttlematch.domain.model.user.UserId;
import com.shuttlematch.domain.repository.RoomRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VerifyOrganizerUseCaseTest {

    private RoomRepository roomRepository;
    private VerifyOrganizerUseCase useCase;
    private Room room;
    private String issuedToken;

    @BeforeEach
    void setUp() {
        roomRepository = mock(RoomRepository.class);
        useCase = new VerifyOrganizerUseCase(roomRepository);

        Room created = Room.create(
                "練習会", OffsetDateTime.now(), null, null, UserId.of(UUID.randomUUID()));
        issuedToken = created.issuedOrganizerToken();
        // 実際の検証は永続化層から読み直した集約に対して行われる(平文は持たない)
        room = Room.reconstitute(
                created.id(), created.shareCode(), created.title(), created.heldAt(),
                created.location(), created.capacity(), created.courtCount(), created.status(),
                created.createdBy(), List.of(), List.of(), created.organizerTokenHash());
        when(roomRepository.findById(room.id())).thenReturn(Optional.of(room));
    }

    @Test
    @DisplayName("正しいトークンなら通る")
    void passesWithCorrectToken() {
        assertThatCode(() -> useCase.execute(room.id(), issuedToken)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("トークンが違えば 403 相当の例外")
    void rejectsWrongToken() {
        assertThatThrownBy(() -> useCase.execute(room.id(), OrganizerToken.generate()))
                .isInstanceOf(OrganizerForbiddenException.class);
    }

    @Test
    @DisplayName("トークンが無ければ 403 相当の例外")
    void rejectsMissingToken() {
        assertThatThrownBy(() -> useCase.execute(room.id(), null))
                .isInstanceOf(OrganizerForbiddenException.class);
    }

    @Test
    @DisplayName("運営者トークン導入前のルーム(ハッシュ無し)は従来どおり誰でも運営操作できる")
    void legacyRoomWithoutHashIsOpen() {
        Room legacy = Room.reconstitute(
                room.id(), room.shareCode(), room.title(), room.heldAt(),
                room.location(), room.capacity(), room.courtCount(), room.status(),
                room.createdBy(), List.of(), List.of(), null);
        when(roomRepository.findById(legacy.id())).thenReturn(Optional.of(legacy));

        assertThatCode(() -> useCase.execute(legacy.id(), null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("ルームが存在しなければ 404 相当の例外")
    void rejectsUnknownRoom() {
        RoomId unknown = RoomId.of(UUID.randomUUID());
        when(roomRepository.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(unknown, issuedToken))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}

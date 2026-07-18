package com.shuttlematch.presentation.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shuttlematch.application.ResourceNotFoundException;
import com.shuttlematch.application.usecase.room.CreateRoomCommand;
import com.shuttlematch.application.usecase.room.CreateRoomUseCase;
import com.shuttlematch.application.usecase.room.GetRoomUseCase;
import com.shuttlematch.domain.model.room.Room;
import com.shuttlematch.domain.model.room.RoomId;
import com.shuttlematch.domain.model.room.RoomStatus;
import com.shuttlematch.domain.model.user.UserId;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({RoomController.class, ParticipantController.class})
class RoomControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CreateRoomUseCase createSessionUseCase;

    @MockitoBean
    private GetRoomUseCase getRoomUseCase;

    @MockitoBean
    private com.shuttlematch.application.usecase.room.GetRoomByShareCodeUseCase getRoomByShareCodeUseCase;

    @MockitoBean
    private com.shuttlematch.application.usecase.room.ListRoomsUseCase listSessionsUseCase;

    @MockitoBean
    private com.shuttlematch.application.usecase.room.AddParticipantUseCase addParticipantUseCase;

    @MockitoBean
    private com.shuttlematch.application.usecase.room.RemoveParticipantUseCase removeParticipantUseCase;

    @MockitoBean
    private com.shuttlematch.application.usecase.room.MarkParticipantLeftUseCase markParticipantLeftUseCase;

    @MockitoBean
    private com.shuttlematch.application.usecase.room.ReactivateParticipantUseCase reactivateParticipantUseCase;

    @MockitoBean
    private com.shuttlematch.application.usecase.room.CloseRoomUseCase closeSessionUseCase;

    @MockitoBean
    private com.shuttlematch.application.usecase.room.QuickCreateRoomUseCase quickCreateSessionUseCase;

    @MockitoBean
    private com.shuttlematch.application.usecase.room.DeleteRoomUseCase deleteRoomUseCase;

    @MockitoBean
    private com.shuttlematch.application.usecase.room.RenameParticipantUseCase renameParticipantUseCase;

    private Room sampleSession() {
        return Room.create(
                "練習会", OffsetDateTime.parse("2026-07-01T18:00:00+09:00"),
                "体育館", 16, UserId.of(UUID.randomUUID()));
    }

    @Test
    @DisplayName("POST create: 201 でセッションを返す")
    void createReturnsCreated() throws Exception {
        when(createSessionUseCase.execute(any(CreateRoomCommand.class))).thenReturn(sampleSession());

        String body = """
                {
                  "title": "練習会",
                  "heldAt": "2026-07-01T18:00:00+09:00",
                  "location": "体育館",
                  "capacity": 16,
                  "createdBy": "%s"
                }
                """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/api/v1/rooms")
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("練習会"))
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    @DisplayName("POST create: 必須項目欠落は 400")
    void createValidationFails() throws Exception {
        mockMvc.perform(post("/api/v1/rooms")
                        .contentType("application/json").content("{\"location\":\"x\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET room: 存在しなければ 404")
    void getNotFound() throws Exception {
        when(getRoomUseCase.execute(any())).thenThrow(new ResourceNotFoundException("なし"));

        mockMvc.perform(get("/api/v1/rooms/{roomId}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST quick: 201 で作成したセッションを返す")
    void quickCreateReturnsCreated() throws Exception {
        when(quickCreateSessionUseCase.execute(
                any(com.shuttlematch.application.usecase.room.QuickCreateRoomCommand.class)))
                .thenReturn(sampleSession());

        String body = """
                {"title": "7/2 夜練", "courtCount": 2, "participantCount": 8, "createdBy": "%s"}
                """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/api/v1/rooms/quick")
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("練習会"));
    }

    @Test
    @DisplayName("POST quick: 参加人数が0以下は 400")
    void quickCreateValidationFails() throws Exception {
        String body = """
                {"title": "x", "courtCount": 1, "participantCount": 0, "createdBy": "%s"}
                """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/api/v1/rooms/quick")
                        .contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST close: 200 で終了済みセッションを返す")
    void closeReturnsSession() throws Exception {
        Room closed = Room.reconstitute(
                RoomId.newId(), "testcode", "練習会",
                OffsetDateTime.parse("2026-07-01T18:00:00+09:00"), null, null, null,
                RoomStatus.CLOSED,
                UserId.of(UUID.randomUUID()), java.util.List.of());
        when(closeSessionUseCase.execute(any(RoomId.class))).thenReturn(closed);

        mockMvc.perform(post("/api/v1/rooms/{roomId}/close", UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
    }

    @Test
    @DisplayName("DELETE room: 204 でルームを削除する")
    void deleteReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/v1/rooms/{roomId}", UUID.randomUUID()))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE room: 存在しなければ 404")
    void deleteNotFound() throws Exception {
        doThrow(new ResourceNotFoundException("なし"))
                .when(deleteRoomUseCase).execute(any(RoomId.class));

        mockMvc.perform(delete("/api/v1/rooms/{roomId}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }
}

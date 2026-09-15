package com.shuttlematch.presentation.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shuttlematch.application.ForbiddenOperationException;
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
    private com.shuttlematch.application.usecase.room.JoinRoomUseCase joinRoomUseCase;

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

    @MockitoBean
    private com.shuttlematch.application.usecase.room.ClaimNextParticipantUseCase claimNextParticipantUseCase;

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
    @DisplayName("GET list: 開催中は公開IDと開催内容だけを返し、roomId・shareCode・参加者名は返さない")
    void listDoesNotExposeRoomId() throws Exception {
        Room room = sampleSession();
        when(listSessionsUseCase.execute(any(), any(), any())).thenReturn(java.util.List.of(room));

        mockMvc.perform(get("/api/v1/rooms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("練習会"))
                .andExpect(jsonPath("$[0].publicId").value(
                        com.shuttlematch.presentation.api.response.PublicRoomId.of(room.id())))
                // 認証が無いため、一覧から roomId が割れると誰でも削除・改変できてしまう。
                .andExpect(jsonPath("$[0].id").doesNotExist())
                .andExpect(jsonPath("$[0].shareCode").doesNotExist())
                .andExpect(jsonPath("$[0].createdBy").doesNotExist())
                .andExpect(jsonPath("$[0].participants").doesNotExist());
    }

    @Test
    @DisplayName("GET list: 終了したルームは roomId を返す(過去の試合表は誰でも見られる)")
    void listExposesRoomIdForClosedRoom() throws Exception {
        Room room = sampleSession();
        room.close();
        when(listSessionsUseCase.execute(any(), any(), any())).thenReturn(java.util.List.of(room));

        mockMvc.perform(get("/api/v1/rooms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("CLOSED"))
                .andExpect(jsonPath("$[0].id").value(room.id().value().toString()))
                // 終了していても shareCode と名簿は伏せたまま。
                .andExpect(jsonPath("$[0].shareCode").doesNotExist())
                .andExpect(jsonPath("$[0].participants").doesNotExist());
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
    @DisplayName("DELETE room: 204 でルームを削除する(共有コードはそのまま渡る)")
    void deleteReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/v1/rooms/{roomId}", UUID.randomUUID())
                        .param("shareCode", "abcd2345"))
                .andExpect(status().isNoContent());

        verify(deleteRoomUseCase).execute(any(RoomId.class), eq("abcd2345"));
    }

    @Test
    @DisplayName("DELETE room: 存在しなければ 404")
    void deleteNotFound() throws Exception {
        doThrow(new ResourceNotFoundException("なし"))
                .when(deleteRoomUseCase).execute(any(RoomId.class), any());

        mockMvc.perform(delete("/api/v1/rooms/{roomId}", UUID.randomUUID())
                        .param("shareCode", "abcd2345"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE room: 共有コードが一致しなければ 403")
    void deleteForbidden() throws Exception {
        doThrow(new ForbiddenOperationException("共有コードが一致しないため削除できません"))
                .when(deleteRoomUseCase).execute(any(RoomId.class), any());

        mockMvc.perform(delete("/api/v1/rooms/{roomId}", UUID.randomUUID())
                        .param("shareCode", "wrongcode"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST leave-bulk: 参加者IDの配列を渡すとまとめて早退にする")
    void leaveBulkMarksAllAsLeft() throws Exception {
        UUID roomId = UUID.randomUUID();
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        Room updated = Room.reconstitute(
                RoomId.of(roomId), "code1234", "テスト", OffsetDateTime.now(), null, null, 1,
                RoomStatus.GENERATED, UserId.of(UUID.randomUUID()), java.util.List.of());
        when(markParticipantLeftUseCase.executeMany(any(RoomId.class), any()))
                .thenReturn(updated);

        String body = String.format(
                "{\"participantIds\":[\"%s\",\"%s\"]}", p1, p2);
        mockMvc.perform(post("/api/v1/rooms/{roomId}/participants/leave-bulk", roomId)
                        .contentType("application/json").content(body))
                .andExpect(status().isOk());

        verify(markParticipantLeftUseCase).executeMany(
                eq(RoomId.of(roomId)),
                eq(java.util.List.of(
                        com.shuttlematch.domain.model.room.ParticipantId.of(p1),
                        com.shuttlematch.domain.model.room.ParticipantId.of(p2))));
    }

    @Test
    @DisplayName("POST leave-bulk: 空配列は 400")
    void leaveBulkRejectsEmptyList() throws Exception {
        mockMvc.perform(post("/api/v1/rooms/{roomId}/participants/leave-bulk", UUID.randomUUID())
                        .contentType("application/json").content("{\"participantIds\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST leave-bulk: 存在しない参加者を含むと 404 で、誰も早退にならない")
    void leaveBulkNotFound() throws Exception {
        doThrow(new ResourceNotFoundException("参加者が見つかりません"))
                .when(markParticipantLeftUseCase).executeMany(any(RoomId.class), any());

        String body = String.format("{\"participantIds\":[\"%s\"]}", UUID.randomUUID());
        mockMvc.perform(post("/api/v1/rooms/{roomId}/participants/leave-bulk", UUID.randomUUID())
                        .contentType("application/json").content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST reactivate-bulk: 参加者IDの配列を渡すとまとめて復帰させる")
    void reactivateBulkMarksAllAsActive() throws Exception {
        UUID roomId = UUID.randomUUID();
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        Room updated = Room.reconstitute(
                RoomId.of(roomId), "code1234", "テスト", OffsetDateTime.now(), null, null, 1,
                RoomStatus.GENERATED, UserId.of(UUID.randomUUID()), java.util.List.of());
        when(reactivateParticipantUseCase.executeMany(any(RoomId.class), any()))
                .thenReturn(updated);

        String body = String.format("{\"participantIds\":[\"%s\",\"%s\"]}", p1, p2);
        mockMvc.perform(post("/api/v1/rooms/{roomId}/participants/reactivate-bulk", roomId)
                        .contentType("application/json").content(body))
                .andExpect(status().isOk());

        verify(reactivateParticipantUseCase).executeMany(
                eq(RoomId.of(roomId)),
                eq(java.util.List.of(
                        com.shuttlematch.domain.model.room.ParticipantId.of(p1),
                        com.shuttlematch.domain.model.room.ParticipantId.of(p2))));
    }

    @Test
    @DisplayName("POST reactivate-bulk: 空配列は 400")
    void reactivateBulkRejectsEmptyList() throws Exception {
        mockMvc.perform(
                        post("/api/v1/rooms/{roomId}/participants/reactivate-bulk", UUID.randomUUID())
                                .contentType("application/json").content("{\"participantIds\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST reactivate-bulk: 存在しない参加者を含むと 404 で、誰も復帰しない")
    void reactivateBulkNotFound() throws Exception {
        doThrow(new ResourceNotFoundException("参加者が見つかりません"))
                .when(reactivateParticipantUseCase).executeMany(any(RoomId.class), any());

        String body = String.format("{\"participantIds\":[\"%s\"]}", UUID.randomUUID());
        mockMvc.perform(
                        post("/api/v1/rooms/{roomId}/participants/reactivate-bulk", UUID.randomUUID())
                                .contentType("application/json").content(body))
                .andExpect(status().isNotFound());
    }
}

package com.shuttlematch.presentation.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shuttlematch.application.ResourceNotFoundException;
import com.shuttlematch.application.usecase.session.CreateSessionCommand;
import com.shuttlematch.application.usecase.session.CreateSessionUseCase;
import com.shuttlematch.application.usecase.session.GetSessionUseCase;
import com.shuttlematch.domain.model.session.Session;
import com.shuttlematch.domain.model.session.SessionId;
import com.shuttlematch.domain.model.session.SessionStatus;
import com.shuttlematch.domain.model.user.UserId;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({SessionController.class, ParticipantController.class})
class SessionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CreateSessionUseCase createSessionUseCase;

    @MockitoBean
    private GetSessionUseCase getSessionUseCase;

    @MockitoBean
    private com.shuttlematch.application.usecase.session.ListSessionsUseCase listSessionsUseCase;

    @MockitoBean
    private com.shuttlematch.application.usecase.session.AddParticipantUseCase addParticipantUseCase;

    @MockitoBean
    private com.shuttlematch.application.usecase.session.RemoveParticipantUseCase removeParticipantUseCase;

    @MockitoBean
    private com.shuttlematch.application.usecase.session.MarkParticipantLeftUseCase markParticipantLeftUseCase;

    @MockitoBean
    private com.shuttlematch.application.usecase.session.ReactivateParticipantUseCase reactivateParticipantUseCase;

    @MockitoBean
    private com.shuttlematch.application.usecase.session.CloseSessionUseCase closeSessionUseCase;

    @MockitoBean
    private com.shuttlematch.application.usecase.session.QuickCreateSessionUseCase quickCreateSessionUseCase;

    @MockitoBean
    private com.shuttlematch.application.usecase.session.RenameParticipantUseCase renameParticipantUseCase;

    private Session sampleSession() {
        return Session.create(
                "練習会", OffsetDateTime.parse("2026-07-01T18:00:00+09:00"),
                "体育館", 16, UserId.of(UUID.randomUUID()));
    }

    @Test
    @DisplayName("POST create: 201 でセッションを返す")
    void createReturnsCreated() throws Exception {
        when(createSessionUseCase.execute(any(CreateSessionCommand.class))).thenReturn(sampleSession());

        String body = """
                {
                  "title": "練習会",
                  "heldAt": "2026-07-01T18:00:00+09:00",
                  "location": "体育館",
                  "capacity": 16,
                  "createdBy": "%s"
                }
                """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/api/v1/sessions")
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("練習会"))
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    @DisplayName("POST create: 必須項目欠落は 400")
    void createValidationFails() throws Exception {
        mockMvc.perform(post("/api/v1/sessions")
                        .contentType("application/json").content("{\"location\":\"x\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET session: 存在しなければ 404")
    void getNotFound() throws Exception {
        when(getSessionUseCase.execute(any())).thenThrow(new ResourceNotFoundException("なし"));

        mockMvc.perform(get("/api/v1/sessions/{sessionId}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST quick: 201 で作成したセッションを返す")
    void quickCreateReturnsCreated() throws Exception {
        when(quickCreateSessionUseCase.execute(
                any(com.shuttlematch.application.usecase.session.QuickCreateSessionCommand.class)))
                .thenReturn(sampleSession());

        String body = """
                {"title": "7/2 夜練", "courtCount": 2, "participantCount": 8, "createdBy": "%s"}
                """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/api/v1/sessions/quick")
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

        mockMvc.perform(post("/api/v1/sessions/quick")
                        .contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST close: 200 で終了済みセッションを返す")
    void closeReturnsSession() throws Exception {
        Session closed = Session.reconstitute(
                SessionId.newId(), "練習会",
                OffsetDateTime.parse("2026-07-01T18:00:00+09:00"), null, null, null,
                SessionStatus.CLOSED,
                UserId.of(UUID.randomUUID()), java.util.List.of());
        when(closeSessionUseCase.execute(any(SessionId.class))).thenReturn(closed);

        mockMvc.perform(post("/api/v1/sessions/{sessionId}/close", UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
    }
}

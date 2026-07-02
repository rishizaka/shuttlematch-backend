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
import com.shuttlematch.domain.model.circle.CircleId;
import com.shuttlematch.domain.model.session.Session;
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

    private final UUID circleId = UUID.randomUUID();

    private Session sampleSession() {
        return Session.create(
                CircleId.of(circleId), "練習会", OffsetDateTime.parse("2026-07-01T18:00:00+09:00"),
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

        mockMvc.perform(post("/api/v1/circles/{circleId}/sessions", circleId)
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("練習会"))
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    @DisplayName("POST create: 必須項目欠落は 400")
    void createValidationFails() throws Exception {
        mockMvc.perform(post("/api/v1/circles/{circleId}/sessions", circleId)
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
}

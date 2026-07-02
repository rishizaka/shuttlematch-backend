package com.shuttlematch.presentation.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shuttlematch.application.usecase.session.GenerateMatchesCommand;
import com.shuttlematch.application.usecase.session.GenerateMatchesUseCase;
import com.shuttlematch.application.usecase.session.GetMatchScheduleUseCase;
import com.shuttlematch.domain.model.match.Match;
import com.shuttlematch.domain.model.match.MatchNumber;
import com.shuttlematch.domain.model.match.MatchSchedule;
import com.shuttlematch.domain.model.match.Pair;
import com.shuttlematch.domain.model.session.ParticipantId;
import com.shuttlematch.domain.model.session.SessionId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MatchController.class)
class MatchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GenerateMatchesUseCase generateMatchesUseCase;

    @MockitoBean
    private GetMatchScheduleUseCase getMatchScheduleUseCase;

    @MockitoBean
    private com.shuttlematch.application.usecase.session.StartSetUseCase startSetUseCase;

    @MockitoBean
    private com.shuttlematch.application.usecase.session.AddSetsUseCase addSetsUseCase;

    @MockitoBean
    private com.shuttlematch.application.usecase.session.ReplanFutureSetsUseCase replanFutureSetsUseCase;

    private final UUID sessionId = UUID.randomUUID();

    private MatchSchedule sampleSchedule() {
        ParticipantId p1 = ParticipantId.newId();
        ParticipantId p2 = ParticipantId.newId();
        ParticipantId p3 = ParticipantId.newId();
        ParticipantId p4 = ParticipantId.newId();
        Match match = Match.of(MatchNumber.of(1), 1, 1, new Pair(p1, p2), new Pair(p3, p4));
        return new MatchSchedule(SessionId.of(sessionId), List.of(match));
    }

    @Test
    @DisplayName("POST generate: 200 でスケジュールを返す")
    void generateReturnsSchedule() throws Exception {
        when(generateMatchesUseCase.execute(any(GenerateMatchesCommand.class)))
                .thenReturn(sampleSchedule());

        mockMvc.perform(post("/api/v1/sessions/{sessionId}/matches/generate", sessionId)
                        .contentType("application/json")
                        .content("{\"matchCount\": 15}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(sessionId.toString()))
                .andExpect(jsonPath("$.matchCount").value(1))
                .andExpect(jsonPath("$.matches[0].matchNumber").value(1))
                .andExpect(jsonPath("$.matches[0].setNumber").value(1))
                .andExpect(jsonPath("$.matches[0].pairA.player1Id").exists());
    }

    @Test
    @DisplayName("POST generate: ボディ省略でもデフォルトで生成できる")
    void generateWorksWithoutBody() throws Exception {
        when(generateMatchesUseCase.execute(any(GenerateMatchesCommand.class)))
                .thenReturn(sampleSchedule());

        mockMvc.perform(post("/api/v1/sessions/{sessionId}/matches/generate", sessionId))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST generate: 参加者不足は 400 を返す")
    void generateReturnsBadRequestWhenTooFewParticipants() throws Exception {
        when(generateMatchesUseCase.execute(any(GenerateMatchesCommand.class)))
                .thenThrow(new IllegalArgumentException("最低 4 人必要です"));

        mockMvc.perform(post("/api/v1/sessions/{sessionId}/matches/generate", sessionId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("最低 4 人必要です"));
    }

    @Test
    @DisplayName("POST generate: matchCount が0以下なら 400(バリデーション)")
    void generateReturnsBadRequestForNonPositiveMatchCount() throws Exception {
        mockMvc.perform(post("/api/v1/sessions/{sessionId}/matches/generate", sessionId)
                        .contentType("application/json")
                        .content("{\"matchCount\": 0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST sets/{n}/start: 200 で更新後スケジュールを返す")
    void startSetReturnsSchedule() throws Exception {
        when(startSetUseCase.execute(any(SessionId.class), org.mockito.ArgumentMatchers.eq(1)))
                .thenReturn(sampleSchedule());

        mockMvc.perform(post("/api/v1/sessions/{sessionId}/matches/sets/{n}/start", sessionId, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matches.length()").value(1));
    }

    @Test
    @DisplayName("POST sets: 200 でセット追加後のスケジュールを返す")
    void addSetsReturnsSchedule() throws Exception {
        when(addSetsUseCase.execute(any(SessionId.class), org.mockito.ArgumentMatchers.eq(3)))
                .thenReturn(sampleSchedule());

        mockMvc.perform(post("/api/v1/sessions/{sessionId}/matches/sets", sessionId)
                        .contentType("application/json")
                        .content("{\"setCount\": 3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matches.length()").value(1));
    }

    @Test
    @DisplayName("POST sets: ボディ省略なら1セット追加する")
    void addSetsDefaultsToOne() throws Exception {
        when(addSetsUseCase.execute(any(SessionId.class), org.mockito.ArgumentMatchers.eq(1)))
                .thenReturn(sampleSchedule());

        mockMvc.perform(post("/api/v1/sessions/{sessionId}/matches/sets", sessionId))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST sets: setCount が0以下なら 400(バリデーション)")
    void addSetsReturnsBadRequestForNonPositive() throws Exception {
        mockMvc.perform(post("/api/v1/sessions/{sessionId}/matches/sets", sessionId)
                        .contentType("application/json")
                        .content("{\"setCount\": 0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST replan: 200 で再編成後のスケジュールを返す")
    void replanReturnsSchedule() throws Exception {
        when(replanFutureSetsUseCase.execute(any(SessionId.class)))
                .thenReturn(sampleSchedule());

        mockMvc.perform(post("/api/v1/sessions/{sessionId}/matches/replan", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matches.length()").value(1));
    }

    @Test
    @DisplayName("GET matches: 200 でスケジュールを返す")
    void getReturnsSchedule() throws Exception {
        when(getMatchScheduleUseCase.execute(any(SessionId.class)))
                .thenReturn(Optional.of(sampleSchedule()));

        mockMvc.perform(get("/api/v1/sessions/{sessionId}/matches", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matches.length()").value(1));
    }

    @Test
    @DisplayName("GET matches: 未生成なら 404 を返す")
    void getReturnsNotFoundWhenAbsent() throws Exception {
        when(getMatchScheduleUseCase.execute(any(SessionId.class)))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/sessions/{sessionId}/matches", sessionId))
                .andExpect(status().isNotFound());
    }
}

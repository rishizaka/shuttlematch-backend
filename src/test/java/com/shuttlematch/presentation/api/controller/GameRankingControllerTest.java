package com.shuttlematch.presentation.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shuttlematch.application.usecase.game.GetRankingUseCase;
import com.shuttlematch.application.usecase.game.SubmitScoreResult;
import com.shuttlematch.application.usecase.game.SubmitScoreUseCase;
import com.shuttlematch.domain.model.game.GameScore;
import com.shuttlematch.domain.model.game.MiniGame;
import com.shuttlematch.domain.model.game.PlayerName;
import com.shuttlematch.domain.model.game.Ranking;
import com.shuttlematch.infrastructure.security.ScoreSubmissionRateLimiter;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(GameRankingController.class)
class GameRankingControllerTest {

    private static final Instant T0 = Instant.parse("2026-08-01T00:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetRankingUseCase getRankingUseCase;

    @MockitoBean
    private SubmitScoreUseCase submitScoreUseCase;

    @MockitoBean
    private ScoreSubmissionRateLimiter rateLimiter;

    @BeforeEach
    void allowByDefault() {
        when(rateLimiter.tryAcquire(anyString())).thenReturn(true);
    }

    private static Ranking ranking(int... scores) {
        List<GameScore> entries = IntStream.range(0, scores.length)
                .mapToObj(i -> new GameScore(
                        MiniGame.COIN, PlayerName.of("P" + i), scores[i], T0.plusSeconds(i)))
                .toList();
        return new Ranking(MiniGame.COIN, entries);
    }

    @Test
    @DisplayName("GET で上位5件を順位つきで返す")
    void getRanking() throws Exception {
        when(getRankingUseCase.execute(MiniGame.COIN)).thenReturn(ranking(90, 80, 70));

        mockMvc.perform(get("/api/v1/games/{game}/ranking", "coin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.game").value("coin"))
                .andExpect(jsonPath("$.entries.length()").value(3))
                .andExpect(jsonPath("$.entries[0].rank").value(1))
                .andExpect(jsonPath("$.entries[0].score").value(90))
                .andExpect(jsonPath("$.entries[2].rank").value(3));
    }

    @Test
    @DisplayName("知らないゲームのスラッグは 400")
    void unknownGame() throws Exception {
        mockMvc.perform(get("/api/v1/games/{game}/ranking", "unknown"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST でスコアを登録し、順位を返す")
    void submit() throws Exception {
        when(submitScoreUseCase.execute(eq(MiniGame.COIN), eq(PlayerName.of("りょう")), eq(75)))
                .thenReturn(new SubmitScoreResult(Optional.of(3), ranking(90, 80, 75)));

        mockMvc.perform(post("/api/v1/games/{game}/ranking", "coin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"playerName":"りょう","score":75}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rankedIn").value(true))
                .andExpect(jsonPath("$.rank").value(3))
                .andExpect(jsonPath("$.ranking.entries.length()").value(3));
    }

    @Test
    @DisplayName("ランクインしなければ rankedIn=false を返す")
    void submitOutOfRange() throws Exception {
        when(submitScoreUseCase.execute(any(), any(), eq(10)))
                .thenReturn(new SubmitScoreResult(Optional.empty(), ranking(90, 80)));

        mockMvc.perform(post("/api/v1/games/{game}/ranking", "coin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"playerName":"おしい","score":10}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rankedIn").value(false))
                .andExpect(jsonPath("$.rank").doesNotExist());
    }

    @Test
    @DisplayName("名前が9文字以上なら 400(登録は呼ばれない)")
    void rejectsLongName() throws Exception {
        mockMvc.perform(post("/api/v1/games/{game}/ranking", "coin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"playerName":"123456789","score":10}
                                """))
                .andExpect(status().isBadRequest());

        verify(submitScoreUseCase, never()).execute(any(), any(), anyInt());
    }

    @Test
    @DisplayName("レート制限にかかったら 429(登録は呼ばれない)")
    void rateLimited() throws Exception {
        when(rateLimiter.tryAcquire(anyString())).thenReturn(false);

        mockMvc.perform(post("/api/v1/games/{game}/ranking", "coin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"playerName":"れんだ","score":10}
                                """))
                .andExpect(status().isTooManyRequests());

        verify(submitScoreUseCase, never()).execute(any(), any(), anyInt());
    }
}

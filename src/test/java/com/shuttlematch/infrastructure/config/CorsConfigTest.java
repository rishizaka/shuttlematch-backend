package com.shuttlematch.infrastructure.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shuttlematch.application.usecase.room.GenerateMatchesUseCase;
import com.shuttlematch.application.usecase.room.GetMatchScheduleUseCase;
import com.shuttlematch.presentation.api.controller.MatchController;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MatchController.class)
@Import(CorsConfig.class)
@TestPropertySource(properties = "app.cors.allowed-origins=http://localhost:5173")
class CorsConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GenerateMatchesUseCase generateMatchesUseCase;
    @MockitoBean
    private GetMatchScheduleUseCase getMatchScheduleUseCase;
    @MockitoBean
    private com.shuttlematch.application.usecase.room.StartSetUseCase startSetUseCase;
    @MockitoBean
    private com.shuttlematch.application.usecase.room.AddSetsUseCase addSetsUseCase;
    @MockitoBean
    private com.shuttlematch.application.usecase.room.ReplanFutureSetsUseCase replanFutureSetsUseCase;
    @MockitoBean
    private com.shuttlematch.application.usecase.room.RevertSetUseCase revertSetUseCase;

    @Test
    @DisplayName("許可オリジンからのプリフライトは Access-Control-Allow-Origin を返す")
    void allowsConfiguredOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/rooms/{id}/matches", UUID.randomUUID())
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test
    @DisplayName("未許可オリジンからのプリフライトは拒否される")
    void rejectsUnknownOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/rooms/{id}/matches", UUID.randomUUID())
                        .header("Origin", "http://evil.example.com")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }
}

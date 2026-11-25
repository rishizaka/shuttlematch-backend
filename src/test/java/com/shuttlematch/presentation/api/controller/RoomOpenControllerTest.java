package com.shuttlematch.presentation.api.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shuttlematch.application.ResourceNotFoundException;
import com.shuttlematch.application.usecase.room.RecordParticipantOpenUseCase;
import com.shuttlematch.domain.model.room.RoomId;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RoomOpenController.class)
class RoomOpenControllerTest {

    // 運営者トークンの検証はインターセプタ経由で全 @WebMvcTest スライスに載るため、
    // ここではモックにして検証自体は素通りさせる(検証の挙動は OrganizerAuthorizationTest)。
    @MockitoBean
    private com.shuttlematch.application.usecase.room.VerifyOrganizerUseCase verifyOrganizerUseCase;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RecordParticipantOpenUseCase recordParticipantOpenUseCase;

    @Test
    @DisplayName("POST で開かれたことを記録し、204 を返す(運営者トークンは要らない)")
    void record() throws Exception {
        UUID roomId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/rooms/{roomId}/participant-opens", roomId))
                .andExpect(status().isNoContent());

        verify(recordParticipantOpenUseCase).execute(eq(RoomId.of(roomId)));
    }

    @Test
    @DisplayName("ルームが無ければ 404")
    void notFound() throws Exception {
        UUID roomId = UUID.randomUUID();
        doThrow(new ResourceNotFoundException("not found"))
                .when(recordParticipantOpenUseCase).execute(eq(RoomId.of(roomId)));

        mockMvc.perform(post("/api/v1/rooms/{roomId}/participant-opens", roomId))
                .andExpect(status().isNotFound());
    }
}

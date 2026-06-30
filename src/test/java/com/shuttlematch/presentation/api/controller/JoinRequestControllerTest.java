package com.shuttlematch.presentation.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shuttlematch.application.usecase.circle.ApplyForMembershipUseCase;
import com.shuttlematch.application.usecase.circle.ApproveJoinRequestUseCase;
import com.shuttlematch.application.usecase.circle.ListJoinRequestsUseCase;
import com.shuttlematch.application.usecase.circle.RejectJoinRequestUseCase;
import com.shuttlematch.domain.model.circle.CircleId;
import com.shuttlematch.domain.model.circle.JoinRequest;
import com.shuttlematch.domain.model.user.UserId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(JoinRequestController.class)
class JoinRequestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ApplyForMembershipUseCase applyForMembershipUseCase;

    @MockitoBean
    private ListJoinRequestsUseCase listJoinRequestsUseCase;

    @MockitoBean
    private ApproveJoinRequestUseCase approveJoinRequestUseCase;

    @MockitoBean
    private RejectJoinRequestUseCase rejectJoinRequestUseCase;

    private final UUID circleId = UUID.randomUUID();

    private JoinRequest sampleRequest() {
        return JoinRequest.apply(CircleId.of(circleId), UserId.of(UUID.randomUUID()));
    }

    @Test
    @DisplayName("POST apply: 201 で申請を返す")
    void applyReturnsCreated() throws Exception {
        when(applyForMembershipUseCase.execute(any(), any())).thenReturn(sampleRequest());

        String body = "{\"userId\":\"%s\"}".formatted(UUID.randomUUID());
        mockMvc.perform(post("/api/v1/circles/{circleId}/join-requests", circleId)
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("POST apply: userId 欠落は 400")
    void applyValidationFails() throws Exception {
        mockMvc.perform(post("/api/v1/circles/{circleId}/join-requests", circleId)
                        .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET list: 申請一覧を返す")
    void listReturnsRequests() throws Exception {
        when(listJoinRequestsUseCase.execute(any(), any())).thenReturn(List.of(sampleRequest()));
        mockMvc.perform(get("/api/v1/circles/{circleId}/join-requests", circleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    @DisplayName("POST approve: 承認した申請を返す")
    void approveReturnsApproved() throws Exception {
        JoinRequest approved = sampleRequest();
        approved.approve();
        when(approveJoinRequestUseCase.execute(any(), any())).thenReturn(approved);

        mockMvc.perform(post("/api/v1/circles/{circleId}/join-requests/{id}/approve",
                        circleId, UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }
}

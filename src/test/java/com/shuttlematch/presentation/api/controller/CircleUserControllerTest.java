package com.shuttlematch.presentation.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shuttlematch.application.usecase.circle.AddMemberUseCase;
import com.shuttlematch.application.usecase.circle.CreateCircleCommand;
import com.shuttlematch.application.usecase.circle.CreateCircleUseCase;
import com.shuttlematch.application.usecase.circle.GetCircleUseCase;
import com.shuttlematch.application.usecase.user.CreateUserCommand;
import com.shuttlematch.application.usecase.user.CreateUserUseCase;
import com.shuttlematch.application.usecase.user.GetUserUseCase;
import com.shuttlematch.domain.model.circle.Circle;
import com.shuttlematch.domain.model.circle.JoinPolicy;
import com.shuttlematch.domain.model.user.User;
import com.shuttlematch.domain.model.user.UserId;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({UserController.class, CircleController.class})
class CircleUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CreateUserUseCase createUserUseCase;
    @MockitoBean
    private GetUserUseCase getUserUseCase;
    @MockitoBean
    private CreateCircleUseCase createCircleUseCase;
    @MockitoBean
    private GetCircleUseCase getCircleUseCase;
    @MockitoBean
    private AddMemberUseCase addMemberUseCase;

    @Test
    @DisplayName("POST users: 201 でユーザーを返す")
    void createUser() throws Exception {
        when(createUserUseCase.execute(any(CreateUserCommand.class)))
                .thenReturn(User.create("田中", "tanaka@example.com"));

        mockMvc.perform(post("/api/v1/users")
                        .contentType("application/json")
                        .content("{\"name\":\"田中\",\"email\":\"tanaka@example.com\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("tanaka@example.com"));
    }

    @Test
    @DisplayName("POST users: 不正なメールは 400")
    void createUserInvalidEmail() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .contentType("application/json")
                        .content("{\"name\":\"田中\",\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST circles: 201 でサークルを返す(招待コード付き)")
    void createCircle() throws Exception {
        Circle circle = Circle.create("バド部", "説明", JoinPolicy.OPEN, UserId.of(UUID.randomUUID()));
        when(createCircleUseCase.execute(any(CreateCircleCommand.class))).thenReturn(circle);

        mockMvc.perform(post("/api/v1/circles")
                        .contentType("application/json")
                        .content("{\"name\":\"バド部\",\"joinPolicy\":\"OPEN\",\"createdBy\":\"%s\"}"
                                .formatted(UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("バド部"))
                .andExpect(jsonPath("$.inviteCode").isNotEmpty())
                .andExpect(jsonPath("$.members[0].role").value("ORGANIZER"));
    }

    @Test
    @DisplayName("POST circles: joinPolicy 欠落は 400")
    void createCircleMissingPolicy() throws Exception {
        mockMvc.perform(post("/api/v1/circles")
                        .contentType("application/json")
                        .content("{\"name\":\"バド部\",\"createdBy\":\"%s\"}".formatted(UUID.randomUUID())))
                .andExpect(status().isBadRequest());
    }
}

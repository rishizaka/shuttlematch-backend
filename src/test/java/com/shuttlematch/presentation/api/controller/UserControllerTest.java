package com.shuttlematch.presentation.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shuttlematch.application.usecase.user.CreateUserCommand;
import com.shuttlematch.application.usecase.user.CreateUserUseCase;
import com.shuttlematch.application.usecase.user.GetUserUseCase;
import com.shuttlematch.domain.model.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CreateUserUseCase createUserUseCase;
    @MockitoBean
    private GetUserUseCase getUserUseCase;

    @Test
    @DisplayName("POST users: 201 でユーザーを返す")
    void createUser() throws Exception {
        when(createUserUseCase.execute(any(CreateUserCommand.class)))
                .thenReturn(User.create("田中", "tanaka@example.com", "hash"));

        mockMvc.perform(post("/api/v1/users")
                        .contentType("application/json")
                        .content("{\"name\":\"田中\",\"email\":\"tanaka@example.com\",\"password\":\"abcd1234\"}"))
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
}

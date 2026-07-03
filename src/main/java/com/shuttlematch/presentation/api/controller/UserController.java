package com.shuttlematch.presentation.api.controller;

import com.shuttlematch.application.usecase.user.CreateGuestUserUseCase;
import com.shuttlematch.application.usecase.user.CreateUserCommand;
import com.shuttlematch.application.usecase.user.CreateUserUseCase;
import com.shuttlematch.application.usecase.user.GetUserUseCase;
import com.shuttlematch.domain.model.user.UserId;
import com.shuttlematch.presentation.api.request.CreateGuestUserRequest;
import com.shuttlematch.presentation.api.request.CreateUserRequest;
import com.shuttlematch.presentation.api.response.UserResponse;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * ユーザーの作成・参照を行う REST コントローラ。
 * 認証(Cognito)導入後は作成は signup フローへ統合される想定。
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final CreateUserUseCase createUserUseCase;
    private final CreateGuestUserUseCase createGuestUserUseCase;
    private final GetUserUseCase getUserUseCase;

    public UserController(
            CreateUserUseCase createUserUseCase,
            CreateGuestUserUseCase createGuestUserUseCase,
            GetUserUseCase getUserUseCase) {
        this.createUserUseCase = createUserUseCase;
        this.createGuestUserUseCase = createGuestUserUseCase;
        this.getUserUseCase = getUserUseCase;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
        return UserResponse.from(createUserUseCase.execute(
                new CreateUserCommand(request.name(), request.email(), request.password())));
    }

    /** ゲストユーザーを発行する。未ログインでルームを作成する際の作成者として使う。 */
    @PostMapping("/guest")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse createGuest(
            @Valid @RequestBody(required = false) CreateGuestUserRequest request) {
        return UserResponse.from(
                createGuestUserUseCase.execute(request == null ? null : request.name()));
    }

    @GetMapping("/{userId}")
    public UserResponse get(@PathVariable UUID userId) {
        return UserResponse.from(getUserUseCase.execute(UserId.of(userId)));
    }
}

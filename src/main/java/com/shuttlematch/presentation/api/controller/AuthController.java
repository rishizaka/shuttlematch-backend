package com.shuttlematch.presentation.api.controller;

import com.shuttlematch.application.usecase.user.LoginUseCase;
import com.shuttlematch.presentation.api.request.LoginRequest;
import com.shuttlematch.presentation.api.response.UserResponse;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 認証(ログイン)を行う REST コントローラ。
 * 現時点ではトークンを発行せず、認証されたユーザー情報を返すのみ(Cognito 導入前の暫定)。
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final LoginUseCase loginUseCase;

    public AuthController(LoginUseCase loginUseCase) {
        this.loginUseCase = loginUseCase;
    }

    @PostMapping("/login")
    public UserResponse login(@Valid @RequestBody LoginRequest request) {
        return UserResponse.from(loginUseCase.execute(request.email(), request.password()));
    }
}

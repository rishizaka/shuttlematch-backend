package com.shuttlematch.presentation.api.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * ユーザー作成(サインアップ)リクエスト。
 */
public record CreateUserRequest(
        @NotBlank String name,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, message = "パスワードは8文字以上にしてください") String password) {
}

package com.shuttlematch.presentation.api.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * ユーザー作成リクエスト。
 */
public record CreateUserRequest(
        @NotBlank String name,
        @NotBlank @Email String email) {
}

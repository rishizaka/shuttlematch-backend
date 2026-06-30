package com.shuttlematch.presentation.api.request;

import jakarta.validation.constraints.NotBlank;

/**
 * ログインリクエスト。
 */
public record LoginRequest(
        @NotBlank String email,
        @NotBlank String password) {
}

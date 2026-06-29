package com.shuttlematch.presentation.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * セッション作成リクエスト。
 * <p>
 * createdBy は本来は認証情報から取得するが、認証導入前のため当面リクエストで受け取る。
 */
public record CreateSessionRequest(
        @NotBlank String title,
        @NotNull OffsetDateTime heldAt,
        String location,
        @Positive Integer capacity,
        @NotNull UUID createdBy) {
}

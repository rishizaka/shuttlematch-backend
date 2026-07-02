package com.shuttlematch.presentation.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

/**
 * かんたんセッション作成リクエスト。参加人数・コート数・タイトルのみ。
 * createdBy は認証導入前のため当面リクエストで受け取る。
 */
public record QuickCreateRoomRequest(
        @NotBlank String title,
        @NotNull @Positive Integer courtCount,
        @NotNull @Positive Integer participantCount,
        @NotNull UUID createdBy) {
}

package com.shuttlematch.presentation.api.request;

import com.shuttlematch.domain.model.circle.JoinPolicy;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * サークル作成リクエスト。
 * createdBy は認証導入前のため当面リクエストで受け取る。
 */
public record CreateCircleRequest(
        @NotBlank String name,
        String description,
        @NotNull JoinPolicy joinPolicy,
        @NotNull UUID createdBy) {
}

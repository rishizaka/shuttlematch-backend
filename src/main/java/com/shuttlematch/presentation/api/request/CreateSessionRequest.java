package com.shuttlematch.presentation.api.request;

import com.shuttlematch.domain.model.session.SessionVisibility;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * セッション作成リクエスト。
 * <p>
 * createdBy は本来は認証情報から取得するが、認証導入前のため当面リクエストで受け取る。
 * visibility を省略した場合は PUBLIC(公開) として扱う。
 */
public record CreateSessionRequest(
        @NotBlank String title,
        @NotNull OffsetDateTime heldAt,
        String location,
        @Positive Integer capacity,
        @Positive Integer courtCount,
        SessionVisibility visibility,
        @NotNull UUID createdBy) {

    public SessionVisibility visibilityOrDefault() {
        return visibility == null ? SessionVisibility.PUBLIC : visibility;
    }
}

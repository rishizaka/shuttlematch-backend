package com.shuttlematch.presentation.api.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 参加者の名前(ニックネーム)変更リクエスト。
 */
public record RenameParticipantRequest(@NotBlank String name) {
}

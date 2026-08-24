package com.shuttlematch.presentation.api.request;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

/**
 * 複数の参加者をまとめて早退にするリクエスト。
 */
public record MarkParticipantsLeftRequest(@NotEmpty List<UUID> participantIds) {
}

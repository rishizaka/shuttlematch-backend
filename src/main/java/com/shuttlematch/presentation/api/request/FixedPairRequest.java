package com.shuttlematch.presentation.api.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * 固定ペアの追加リクエスト。常に同じチームで組む2人の参加者 ID。
 */
public record FixedPairRequest(
        @NotNull UUID participantA,
        @NotNull UUID participantB) {
}

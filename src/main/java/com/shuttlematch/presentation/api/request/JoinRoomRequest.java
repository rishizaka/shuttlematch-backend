package com.shuttlematch.presentation.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 受付中ルームへの自己参加リクエスト。名前は必須。
 * 参加すると参加順で番号が自動採番される。
 */
public record JoinRoomRequest(@NotBlank @Size(max = 30) String name) {
}

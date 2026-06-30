package com.shuttlematch.presentation.api.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * サークル参加申請リクエスト。
 * userId は認証導入前のため当面リクエストで受け取る。
 */
public record ApplyForMembershipRequest(@NotNull UUID userId) {
}

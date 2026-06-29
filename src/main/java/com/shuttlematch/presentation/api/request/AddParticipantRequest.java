package com.shuttlematch.presentation.api.request;

import java.util.UUID;

/**
 * 参加登録リクエスト。userId(登録ユーザー)または guestName(ゲスト)のいずれか一方を指定する。
 */
public record AddParticipantRequest(UUID userId, String guestName) {
}

package com.shuttlematch.presentation.api.request;

import jakarta.validation.constraints.Size;

/**
 * 「番号のまま」の枠へ自動採番で参加するリクエスト。名前は任意(未入力なら「ゲスト」)。
 * JoinRoomRequest と違い name を必須にしないのは、簡易作成ルームは受付とは別物で、
 * 番号だけで運用したい人もいるため。
 */
public record ClaimNextParticipantRequest(@Size(max = 30) String name) {
}

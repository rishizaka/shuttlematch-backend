package com.shuttlematch.presentation.api.response;

/**
 * 自己参加のレスポンス。参加した本人の participantId と割り当てられた番号、
 * および最新のルーム状態(参加者一覧を含む)を返す。
 * クライアントは participantId を端末に保存して「自分」を識別する(試合ハイライト用)。
 */
public record JoinResponse(String participantId, int number, RoomResponse room) {
}

package com.shuttlematch.presentation.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 「番号のまま」の枠へ自動採番で参加するリクエスト。名前は必須。
 * <p>
 * 以前は任意(未入力なら「ゲスト」)にしていたが、実際に空欄のまま参加する人が出ると、
 * 「番号のまま(誰も参加していない)」枠と「guestName="ゲスト"の実在の参加者」が
 * 運営者から見分けづらくなる混乱が起きたため、JoinRoomRequest と同じく必須にした。
 */
public record ClaimNextParticipantRequest(@NotBlank @Size(max = 30) String name) {
}

package com.shuttlematch.presentation.api.request;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

/**
 * 複数の参加者IDをまとめて渡すリクエスト。まとめて早退・まとめて復帰など、
 * 「参加者を複数指定して一括で何かする」系のエンドポイントで共用する。
 */
public record ParticipantIdsRequest(@NotEmpty List<UUID> participantIds) {
}

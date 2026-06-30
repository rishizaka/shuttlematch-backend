package com.shuttlematch.presentation.api.response;

import com.shuttlematch.domain.model.circle.JoinRequest;

import java.time.OffsetDateTime;

/**
 * 参加申請のレスポンス表現。
 */
public record JoinRequestResponse(
        String id,
        String circleId,
        String userId,
        String status,
        OffsetDateTime requestedAt,
        OffsetDateTime decidedAt) {

    public static JoinRequestResponse from(JoinRequest request) {
        return new JoinRequestResponse(
                request.id().value().toString(),
                request.circleId().value().toString(),
                request.userId().value().toString(),
                request.status().name(),
                request.requestedAt(),
                request.decidedAt());
    }
}

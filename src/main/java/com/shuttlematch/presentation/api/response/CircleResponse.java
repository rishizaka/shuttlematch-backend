package com.shuttlematch.presentation.api.response;

import com.shuttlematch.domain.model.circle.Circle;

import java.util.List;

/**
 * サークルのレスポンス表現。
 */
public record CircleResponse(
        String id,
        String name,
        String description,
        String inviteCode,
        String joinPolicy,
        String createdBy,
        List<MemberResponse> members) {

    public static CircleResponse from(Circle circle) {
        List<MemberResponse> members = circle.members().stream()
                .map(MemberResponse::from)
                .toList();
        return new CircleResponse(
                circle.id().value().toString(),
                circle.name(),
                circle.description(),
                circle.inviteCode().value(),
                circle.joinPolicy().name(),
                circle.createdBy().value().toString(),
                members);
    }
}

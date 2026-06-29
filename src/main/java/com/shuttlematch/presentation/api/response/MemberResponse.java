package com.shuttlematch.presentation.api.response;

import com.shuttlematch.domain.model.circle.Member;

/**
 * サークルメンバーのレスポンス表現。
 */
public record MemberResponse(String userId, String role) {

    public static MemberResponse from(Member member) {
        return new MemberResponse(member.userId().value().toString(), member.role().name());
    }
}

package com.shuttlematch.domain.model.circle;

import com.shuttlematch.domain.model.user.UserId;

import java.util.Objects;

/**
 * サークルメンバー(エンティティ)。ユーザーとロールの組。
 */
public record Member(UserId userId, MemberRole role) {

    public Member {
        Objects.requireNonNull(userId, "userId は必須です");
        Objects.requireNonNull(role, "role は必須です");
    }

    public boolean isOrganizer() {
        return role == MemberRole.ORGANIZER;
    }
}

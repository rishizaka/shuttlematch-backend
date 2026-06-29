package com.shuttlematch.presentation.api.request;

import com.shuttlematch.domain.model.circle.MemberRole;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * メンバー追加リクエスト。role を省略した場合は PLAYER として扱う。
 */
public record AddMemberRequest(@NotNull UUID userId, MemberRole role) {

    public MemberRole roleOrDefault() {
        return role == null ? MemberRole.PLAYER : role;
    }
}

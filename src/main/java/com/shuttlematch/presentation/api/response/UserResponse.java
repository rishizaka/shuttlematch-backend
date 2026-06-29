package com.shuttlematch.presentation.api.response;

import com.shuttlematch.domain.model.user.User;

/**
 * ユーザーのレスポンス表現。
 */
public record UserResponse(String id, String name, String email) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.id().value().toString(),
                user.name(),
                user.email());
    }
}

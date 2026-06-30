package com.shuttlematch.application.usecase.user;

/**
 * ユーザー作成ユースケースの入力。
 */
public record CreateUserCommand(String name, String email, String password) {
}

package com.shuttlematch.application.usecase.user;

import com.shuttlematch.domain.model.user.User;
import com.shuttlematch.domain.repository.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ゲストユーザー(メール・パスワードなし)を作成するユースケース。
 * 未ログインでルームを作成する際に、作成者の識別子として発行する。
 */
@Service
public class CreateGuestUserUseCase {

    /** 名前未指定時の既定表示名。 */
    static final String DEFAULT_NAME = "ゲスト";

    private final UserRepository userRepository;

    public CreateGuestUserUseCase(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public User execute(String name) {
        String resolved = name == null || name.isBlank() ? DEFAULT_NAME : name.trim();
        return userRepository.save(User.createGuest(resolved));
    }
}

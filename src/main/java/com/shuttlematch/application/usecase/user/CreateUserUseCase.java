package com.shuttlematch.application.usecase.user;

import com.shuttlematch.domain.model.user.User;
import com.shuttlematch.domain.repository.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ユーザーを作成するユースケース。
 */
@Service
public class CreateUserUseCase {

    private final UserRepository userRepository;

    public CreateUserUseCase(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public User execute(CreateUserCommand command) {
        if (userRepository.existsByEmail(command.email())) {
            throw new IllegalArgumentException("このメールアドレスは既に登録されています: " + command.email());
        }
        User user = User.create(command.name(), command.email());
        return userRepository.save(user);
    }
}

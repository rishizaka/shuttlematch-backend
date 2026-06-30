package com.shuttlematch.application.usecase.user;

import com.shuttlematch.domain.model.user.User;
import com.shuttlematch.domain.repository.UserRepository;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ユーザーを作成するユースケース。パスワードは BCrypt でハッシュ化して保存する。
 */
@Service
public class CreateUserUseCase {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public CreateUserUseCase(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User execute(CreateUserCommand command) {
        if (userRepository.existsByEmail(command.email())) {
            throw new IllegalArgumentException("このメールアドレスは既に登録されています: " + command.email());
        }
        if (command.password() == null || command.password().isBlank()) {
            throw new IllegalArgumentException("password は必須です");
        }
        User user = User.create(command.name(), command.email(),
                passwordEncoder.encode(command.password()));
        return userRepository.save(user);
    }
}

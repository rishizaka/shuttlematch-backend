package com.shuttlematch.application.usecase.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shuttlematch.application.InvalidCredentialsException;
import com.shuttlematch.domain.model.user.User;
import com.shuttlematch.domain.model.user.UserId;
import com.shuttlematch.domain.repository.UserRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class LoginUseCaseTest {

    private FakeUserRepository userRepository;
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    private LoginUseCase useCase;

    @BeforeEach
    void setUp() {
        userRepository = new FakeUserRepository();
        useCase = new LoginUseCase(userRepository, encoder);
    }

    @Test
    @DisplayName("正しいメール+パスワードでログインできる")
    void loginsWithValidCredentials() {
        userRepository.save(User.create("田中", "a@example.com", encoder.encode("secret123")));
        User user = useCase.execute("a@example.com", "secret123");
        assertThat(user.email()).isEqualTo("a@example.com");
    }

    @Test
    @DisplayName("パスワードが違うと InvalidCredentialsException")
    void rejectsWrongPassword() {
        userRepository.save(User.create("田中", "a@example.com", encoder.encode("secret123")));
        assertThatThrownBy(() -> useCase.execute("a@example.com", "wrong"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    @DisplayName("存在しないメールは InvalidCredentialsException")
    void rejectsUnknownEmail() {
        assertThatThrownBy(() -> useCase.execute("none@example.com", "x"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    @DisplayName("パスワード未設定ユーザーは abcd1234 でログインできる")
    void loginsWithDefaultPasswordWhenNotSet() {
        // password_hash が null の既存ユーザー
        userRepository.save(User.reconstitute(UserId.newId(), null, "旧ユーザー", "old@example.com", null));
        User user = useCase.execute("old@example.com", LoginUseCase.DEFAULT_PASSWORD);
        assertThat(user.email()).isEqualTo("old@example.com");
    }

    @Test
    @DisplayName("パスワード未設定ユーザーに abcd1234 以外は通らない")
    void rejectsNonDefaultForUnsetPassword() {
        userRepository.save(User.reconstitute(UserId.newId(), null, "旧ユーザー", "old@example.com", null));
        assertThatThrownBy(() -> useCase.execute("old@example.com", "somethingelse"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    private static final class FakeUserRepository implements UserRepository {
        private final Map<String, User> byEmail = new HashMap<>();

        @Override
        public User save(User user) {
            byEmail.put(user.email(), user);
            return user;
        }

        @Override
        public Optional<User> findById(UserId userId) {
            return byEmail.values().stream().filter(u -> u.id().equals(userId)).findFirst();
        }

        @Override
        public Optional<User> findByEmail(String email) {
            return Optional.ofNullable(byEmail.get(email));
        }

        @Override
        public boolean existsByEmail(String email) {
            return byEmail.containsKey(email);
        }
    }
}

package com.shuttlematch.application.usecase.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shuttlematch.domain.model.user.User;
import com.shuttlematch.domain.model.user.UserId;
import com.shuttlematch.domain.repository.UserRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class CreateUserUseCaseTest {

    private FakeUserRepository userRepository;
    private CreateUserUseCase useCase;

    @BeforeEach
    void setUp() {
        userRepository = new FakeUserRepository();
        useCase = new CreateUserUseCase(userRepository, new PlainPasswordEncoder());
    }

    private User create(String email) {
        return useCase.execute(new CreateUserCommand("名前", email, "password123"));
    }

    @Test
    @DisplayName("ユーザーを作成できる")
    void createsUser() {
        User user = create("a@example.com");
        assertThat(user.email()).isEqualTo("a@example.com");
        assertThat(userRepository.findByEmail("a@example.com")).isPresent();
    }

    @Test
    @DisplayName("重複メールアドレスは作成できない")
    void rejectsDuplicateEmail() {
        create("dup@example.com");
        assertThatThrownBy(() -> create("dup@example.com"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static final class PlainPasswordEncoder implements PasswordEncoder {
        @Override
        public String encode(CharSequence rawPassword) {
            return rawPassword.toString();
        }

        @Override
        public boolean matches(CharSequence rawPassword, String encodedPassword) {
            return rawPassword.toString().equals(encodedPassword);
        }
    }

    private static final class FakeUserRepository implements UserRepository {
        private final Map<String, User> byEmail = new HashMap<>();
        private final Map<UserId, User> byId = new HashMap<>();

        @Override
        public User save(User user) {
            byEmail.put(user.email(), user);
            byId.put(user.id(), user);
            return user;
        }

        @Override
        public Optional<User> findById(UserId userId) {
            return Optional.ofNullable(byId.get(userId));
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

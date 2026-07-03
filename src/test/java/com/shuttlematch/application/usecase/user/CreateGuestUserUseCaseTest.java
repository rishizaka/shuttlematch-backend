package com.shuttlematch.application.usecase.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.shuttlematch.domain.model.user.User;
import com.shuttlematch.domain.model.user.UserId;
import com.shuttlematch.domain.repository.UserRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CreateGuestUserUseCaseTest {

    private FakeUserRepository userRepository;
    private CreateGuestUserUseCase useCase;

    @BeforeEach
    void setUp() {
        userRepository = new FakeUserRepository();
        useCase = new CreateGuestUserUseCase(userRepository);
    }

    @Test
    @DisplayName("名前を指定してゲストユーザーを作成できる(メール・パスワードなし)")
    void createsGuestWithName() {
        User user = useCase.execute("たろう");
        assertThat(user.name()).isEqualTo("たろう");
        assertThat(user.email()).isNull();
        assertThat(user.hasPassword()).isFalse();
        assertThat(user.isGuest()).isTrue();
        assertThat(userRepository.findById(user.id())).isPresent();
    }

    @Test
    @DisplayName("名前未指定(null・空白)なら既定名「ゲスト」になる")
    void defaultsNameWhenBlank() {
        assertThat(useCase.execute(null).name()).isEqualTo("ゲスト");
        assertThat(useCase.execute("  ").name()).isEqualTo("ゲスト");
    }

    @Test
    @DisplayName("複数のゲストを重複なく発行できる")
    void issuesDistinctGuests() {
        User first = useCase.execute(null);
        User second = useCase.execute(null);
        assertThat(first.id()).isNotEqualTo(second.id());
    }

    private static final class FakeUserRepository implements UserRepository {
        private final Map<UserId, User> store = new HashMap<>();

        @Override
        public User save(User user) {
            store.put(user.id(), user);
            return user;
        }

        @Override
        public Optional<User> findById(UserId userId) {
            return Optional.ofNullable(store.get(userId));
        }

        @Override
        public Optional<User> findByEmail(String email) {
            return store.values().stream()
                    .filter(u -> email.equals(u.email()))
                    .findFirst();
        }

        @Override
        public boolean existsByEmail(String email) {
            return findByEmail(email).isPresent();
        }
    }
}

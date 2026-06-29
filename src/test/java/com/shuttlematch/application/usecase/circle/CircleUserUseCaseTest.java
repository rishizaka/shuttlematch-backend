package com.shuttlematch.application.usecase.circle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shuttlematch.application.ResourceNotFoundException;
import com.shuttlematch.application.usecase.user.CreateUserCommand;
import com.shuttlematch.application.usecase.user.CreateUserUseCase;
import com.shuttlematch.domain.model.circle.Circle;
import com.shuttlematch.domain.model.circle.CircleId;
import com.shuttlematch.domain.model.circle.JoinPolicy;
import com.shuttlematch.domain.model.circle.MemberRole;
import com.shuttlematch.domain.model.user.User;
import com.shuttlematch.domain.model.user.UserId;
import com.shuttlematch.domain.repository.CircleRepository;
import com.shuttlematch.domain.repository.UserRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CircleUserUseCaseTest {

    private FakeUserRepository userRepository;
    private FakeCircleRepository circleRepository;
    private CreateUserUseCase createUserUseCase;
    private CreateCircleUseCase createCircleUseCase;
    private AddMemberUseCase addMemberUseCase;

    @BeforeEach
    void setUp() {
        userRepository = new FakeUserRepository();
        circleRepository = new FakeCircleRepository();
        createUserUseCase = new CreateUserUseCase(userRepository);
        createCircleUseCase = new CreateCircleUseCase(circleRepository, userRepository);
        addMemberUseCase = new AddMemberUseCase(circleRepository, userRepository);
    }

    private User createUser(String email) {
        return createUserUseCase.execute(new CreateUserCommand("名前", email));
    }

    @Test
    @DisplayName("ユーザーを作成できる")
    void createsUser() {
        User user = createUser("a@example.com");
        assertThat(userRepository.findById(user.id())).isPresent();
    }

    @Test
    @DisplayName("重複メールアドレスは作成できない")
    void rejectsDuplicateEmail() {
        createUser("dup@example.com");
        assertThatThrownBy(() -> createUser("dup@example.com"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("サークルを作成すると作成者が ORGANIZER メンバーになる")
    void createsCircleWithOrganizer() {
        User owner = createUser("owner@example.com");
        Circle circle = createCircleUseCase.execute(
                new CreateCircleCommand("部活", null, JoinPolicy.OPEN, owner.id()));

        assertThat(circleRepository.findById(circle.id())).isPresent();
        assertThat(circle.members()).hasSize(1);
        assertThat(circle.members().get(0).isOrganizer()).isTrue();
    }

    @Test
    @DisplayName("存在しないユーザーではサークルを作成できない")
    void cannotCreateCircleWithMissingUser() {
        assertThatThrownBy(() -> createCircleUseCase.execute(
                new CreateCircleCommand("部活", null, JoinPolicy.OPEN, UserId.newId())))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("サークルにメンバーを追加できる")
    void addsMember() {
        User owner = createUser("owner2@example.com");
        User member = createUser("member@example.com");
        Circle circle = createCircleUseCase.execute(
                new CreateCircleCommand("部活", null, JoinPolicy.OPEN, owner.id()));

        Circle updated = addMemberUseCase.execute(
                new AddMemberCommand(circle.id(), member.id(), MemberRole.PLAYER));

        assertThat(updated.members()).hasSize(2);
    }

    @Test
    @DisplayName("存在しないサークルへのメンバー追加は ResourceNotFoundException")
    void addMemberToMissingCircle() {
        User member = createUser("m2@example.com");
        assertThatThrownBy(() -> addMemberUseCase.execute(
                new AddMemberCommand(CircleId.newId(), member.id(), MemberRole.PLAYER)))
                .isInstanceOf(ResourceNotFoundException.class);
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
        public boolean existsByEmail(String email) {
            return store.values().stream().anyMatch(u -> u.email().equals(email));
        }
    }

    private static final class FakeCircleRepository implements CircleRepository {
        private final Map<CircleId, Circle> store = new HashMap<>();

        @Override
        public Circle save(Circle circle) {
            store.put(circle.id(), circle);
            return circle;
        }

        @Override
        public Optional<Circle> findById(CircleId circleId) {
            return Optional.ofNullable(store.get(circleId));
        }
    }
}

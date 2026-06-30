package com.shuttlematch.application.usecase.circle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shuttlematch.application.ResourceNotFoundException;
import com.shuttlematch.domain.model.circle.Circle;
import com.shuttlematch.domain.model.circle.CircleId;
import com.shuttlematch.domain.model.circle.JoinPolicy;
import com.shuttlematch.domain.model.circle.JoinRequest;
import com.shuttlematch.domain.model.circle.JoinRequestId;
import com.shuttlematch.domain.model.circle.JoinRequestStatus;
import com.shuttlematch.domain.model.user.User;
import com.shuttlematch.domain.model.user.UserId;
import com.shuttlematch.domain.repository.CircleRepository;
import com.shuttlematch.domain.repository.JoinRequestRepository;
import com.shuttlematch.domain.repository.UserRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JoinRequestUseCaseTest {

    private FakeUserRepository userRepository;
    private FakeCircleRepository circleRepository;
    private FakeJoinRequestRepository joinRequestRepository;
    private ApplyForMembershipUseCase applyUseCase;
    private ApproveJoinRequestUseCase approveUseCase;
    private RejectJoinRequestUseCase rejectUseCase;
    private ListJoinRequestsUseCase listUseCase;

    @BeforeEach
    void setUp() {
        userRepository = new FakeUserRepository();
        circleRepository = new FakeCircleRepository();
        joinRequestRepository = new FakeJoinRequestRepository();
        applyUseCase = new ApplyForMembershipUseCase(
                circleRepository, userRepository, joinRequestRepository);
        approveUseCase = new ApproveJoinRequestUseCase(circleRepository, joinRequestRepository);
        rejectUseCase = new RejectJoinRequestUseCase(joinRequestRepository);
        listUseCase = new ListJoinRequestsUseCase(joinRequestRepository);
    }

    private User user() {
        User u = User.create("名前", "u" + System.nanoTime() + "@example.com", "hash");
        return userRepository.save(u);
    }

    private Circle circle(UserId owner) {
        Circle c = Circle.create("部活", null, JoinPolicy.APPROVAL, owner);
        return circleRepository.save(c);
    }

    @Test
    @DisplayName("非メンバーは参加申請でき PENDING になる")
    void applies() {
        User owner = user();
        User applicant = user();
        Circle c = circle(owner.id());

        JoinRequest request = applyUseCase.execute(c.id(), applicant.id());

        assertThat(request.status()).isEqualTo(JoinRequestStatus.PENDING);
        assertThat(listUseCase.execute(c.id(), JoinRequestStatus.PENDING)).hasSize(1);
    }

    @Test
    @DisplayName("既にメンバーなら申請できない")
    void cannotApplyWhenAlreadyMember() {
        User owner = user();
        Circle c = circle(owner.id());
        assertThatThrownBy(() -> applyUseCase.execute(c.id(), owner.id()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("申請中の重複申請はできない")
    void cannotApplyTwice() {
        User owner = user();
        User applicant = user();
        Circle c = circle(owner.id());
        applyUseCase.execute(c.id(), applicant.id());
        assertThatThrownBy(() -> applyUseCase.execute(c.id(), applicant.id()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("存在しないユーザーの申請は ResourceNotFoundException")
    void applyMissingUser() {
        User owner = user();
        Circle c = circle(owner.id());
        assertThatThrownBy(() -> applyUseCase.execute(c.id(), UserId.newId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("承認すると申請者がメンバーに追加され APPROVED になる")
    void approvesAndAddsMember() {
        User owner = user();
        User applicant = user();
        Circle c = circle(owner.id());
        JoinRequest request = applyUseCase.execute(c.id(), applicant.id());

        JoinRequest approved = approveUseCase.execute(c.id(), request.id());

        assertThat(approved.status()).isEqualTo(JoinRequestStatus.APPROVED);
        Circle updated = circleRepository.findById(c.id()).orElseThrow();
        assertThat(updated.members().stream().anyMatch(m -> m.userId().equals(applicant.id())))
                .isTrue();
    }

    @Test
    @DisplayName("却下すると REJECTED になりメンバーには追加されない")
    void rejects() {
        User owner = user();
        User applicant = user();
        Circle c = circle(owner.id());
        JoinRequest request = applyUseCase.execute(c.id(), applicant.id());

        JoinRequest rejected = rejectUseCase.execute(c.id(), request.id());

        assertThat(rejected.status()).isEqualTo(JoinRequestStatus.REJECTED);
        Circle updated = circleRepository.findById(c.id()).orElseThrow();
        assertThat(updated.members()).hasSize(1);
    }

    @Test
    @DisplayName("別サークルの申請IDを指定すると ResourceNotFoundException")
    void approveWrongCircle() {
        User owner = user();
        User applicant = user();
        Circle c = circle(owner.id());
        JoinRequest request = applyUseCase.execute(c.id(), applicant.id());

        assertThatThrownBy(() -> approveUseCase.execute(CircleId.newId(), request.id()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- インメモリ実装 ---

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
            return store.values().stream().filter(u -> u.email().equals(email)).findFirst();
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

    private static final class FakeJoinRequestRepository implements JoinRequestRepository {
        private final Map<JoinRequestId, JoinRequest> store = new HashMap<>();

        @Override
        public JoinRequest save(JoinRequest joinRequest) {
            store.put(joinRequest.id(), joinRequest);
            return joinRequest;
        }

        @Override
        public Optional<JoinRequest> findById(JoinRequestId id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public List<JoinRequest> findByCircleIdAndStatus(CircleId circleId, JoinRequestStatus status) {
            return store.values().stream()
                    .filter(r -> r.circleId().equals(circleId) && r.status() == status)
                    .toList();
        }

        @Override
        public boolean existsPending(CircleId circleId, UserId userId) {
            return store.values().stream().anyMatch(r ->
                    r.circleId().equals(circleId)
                            && r.userId().equals(userId)
                            && r.status() == JoinRequestStatus.PENDING);
        }
    }
}

package com.shuttlematch.application.usecase.circle;

import com.shuttlematch.application.ResourceNotFoundException;
import com.shuttlematch.domain.model.circle.Circle;
import com.shuttlematch.domain.model.circle.CircleId;
import com.shuttlematch.domain.model.circle.JoinRequest;
import com.shuttlematch.domain.model.user.UserId;
import com.shuttlematch.domain.repository.CircleRepository;
import com.shuttlematch.domain.repository.JoinRequestRepository;
import com.shuttlematch.domain.repository.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * サークルへの参加申請を行うユースケース。
 */
@Service
public class ApplyForMembershipUseCase {

    private final CircleRepository circleRepository;
    private final UserRepository userRepository;
    private final JoinRequestRepository joinRequestRepository;

    public ApplyForMembershipUseCase(
            CircleRepository circleRepository,
            UserRepository userRepository,
            JoinRequestRepository joinRequestRepository) {
        this.circleRepository = circleRepository;
        this.userRepository = userRepository;
        this.joinRequestRepository = joinRequestRepository;
    }

    @Transactional
    public JoinRequest execute(CircleId circleId, UserId userId) {
        Circle circle = circleRepository.findById(circleId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "サークルが見つかりません: " + circleId.value()));
        if (userRepository.findById(userId).isEmpty()) {
            throw new ResourceNotFoundException("ユーザーが見つかりません: " + userId.value());
        }
        boolean alreadyMember = circle.members().stream()
                .anyMatch(m -> userId.equals(m.userId()));
        if (alreadyMember) {
            throw new IllegalArgumentException("既にこのサークルのメンバーです");
        }
        if (joinRequestRepository.existsPending(circleId, userId)) {
            throw new IllegalArgumentException("既に参加申請を行っています");
        }
        return joinRequestRepository.save(JoinRequest.apply(circleId, userId));
    }
}

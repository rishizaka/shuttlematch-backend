package com.shuttlematch.application.usecase.circle;

import com.shuttlematch.application.ResourceNotFoundException;
import com.shuttlematch.domain.model.circle.Circle;
import com.shuttlematch.domain.model.circle.CircleId;
import com.shuttlematch.domain.model.circle.JoinRequest;
import com.shuttlematch.domain.model.circle.JoinRequestId;
import com.shuttlematch.domain.model.circle.MemberRole;
import com.shuttlematch.domain.repository.CircleRepository;
import com.shuttlematch.domain.repository.JoinRequestRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 参加申請を承認し、申請者をサークルメンバー(プレイヤー)に追加するユースケース。
 */
@Service
public class ApproveJoinRequestUseCase {

    private final CircleRepository circleRepository;
    private final JoinRequestRepository joinRequestRepository;

    public ApproveJoinRequestUseCase(
            CircleRepository circleRepository,
            JoinRequestRepository joinRequestRepository) {
        this.circleRepository = circleRepository;
        this.joinRequestRepository = joinRequestRepository;
    }

    @Transactional
    public JoinRequest execute(CircleId circleId, JoinRequestId requestId) {
        JoinRequest request = loadRequestInCircle(circleId, requestId);

        Circle circle = circleRepository.findById(circleId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "サークルが見つかりません: " + circleId.value()));

        request.approve();
        // 既にメンバーの場合は addMember が弾くため、未参加時のみ追加する。
        boolean alreadyMember = circle.members().stream()
                .anyMatch(m -> request.userId().equals(m.userId()));
        if (!alreadyMember) {
            circle.addMember(request.userId(), MemberRole.PLAYER);
            circleRepository.save(circle);
        }
        return joinRequestRepository.save(request);
    }

    private JoinRequest loadRequestInCircle(CircleId circleId, JoinRequestId requestId) {
        JoinRequest request = joinRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "参加申請が見つかりません: " + requestId.value()));
        if (!request.circleId().equals(circleId)) {
            throw new ResourceNotFoundException("参加申請が見つかりません: " + requestId.value());
        }
        return request;
    }
}

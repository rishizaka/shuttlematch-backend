package com.shuttlematch.application.usecase.circle;

import com.shuttlematch.application.ResourceNotFoundException;
import com.shuttlematch.domain.model.circle.CircleId;
import com.shuttlematch.domain.model.circle.JoinRequest;
import com.shuttlematch.domain.model.circle.JoinRequestId;
import com.shuttlematch.domain.repository.JoinRequestRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 参加申請を却下するユースケース。
 */
@Service
public class RejectJoinRequestUseCase {

    private final JoinRequestRepository joinRequestRepository;

    public RejectJoinRequestUseCase(JoinRequestRepository joinRequestRepository) {
        this.joinRequestRepository = joinRequestRepository;
    }

    @Transactional
    public JoinRequest execute(CircleId circleId, JoinRequestId requestId) {
        JoinRequest request = joinRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "参加申請が見つかりません: " + requestId.value()));
        if (!request.circleId().equals(circleId)) {
            throw new ResourceNotFoundException("参加申請が見つかりません: " + requestId.value());
        }
        request.reject();
        return joinRequestRepository.save(request);
    }
}

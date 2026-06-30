package com.shuttlematch.application.usecase.circle;

import com.shuttlematch.domain.model.circle.CircleId;
import com.shuttlematch.domain.model.circle.JoinRequest;
import com.shuttlematch.domain.model.circle.JoinRequestStatus;
import com.shuttlematch.domain.repository.JoinRequestRepository;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * サークルの参加申請一覧を取得するユースケース(オーガナイザー用)。
 */
@Service
public class ListJoinRequestsUseCase {

    private final JoinRequestRepository joinRequestRepository;

    public ListJoinRequestsUseCase(JoinRequestRepository joinRequestRepository) {
        this.joinRequestRepository = joinRequestRepository;
    }

    @Transactional(readOnly = true)
    public List<JoinRequest> execute(CircleId circleId, JoinRequestStatus status) {
        return joinRequestRepository.findByCircleIdAndStatus(circleId, status);
    }
}

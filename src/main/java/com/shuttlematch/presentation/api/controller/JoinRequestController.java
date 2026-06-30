package com.shuttlematch.presentation.api.controller;

import com.shuttlematch.application.usecase.circle.ApplyForMembershipUseCase;
import com.shuttlematch.application.usecase.circle.ApproveJoinRequestUseCase;
import com.shuttlematch.application.usecase.circle.ListJoinRequestsUseCase;
import com.shuttlematch.application.usecase.circle.RejectJoinRequestUseCase;
import com.shuttlematch.domain.model.circle.CircleId;
import com.shuttlematch.domain.model.circle.JoinRequestId;
import com.shuttlematch.domain.model.circle.JoinRequestStatus;
import com.shuttlematch.domain.model.user.UserId;
import com.shuttlematch.presentation.api.request.ApplyForMembershipRequest;
import com.shuttlematch.presentation.api.response.JoinRequestResponse;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * サークル参加申請の作成・一覧・承認・却下を行う REST コントローラ。
 */
@RestController
@RequestMapping("/api/v1/circles/{circleId}/join-requests")
public class JoinRequestController {

    private final ApplyForMembershipUseCase applyForMembershipUseCase;
    private final ListJoinRequestsUseCase listJoinRequestsUseCase;
    private final ApproveJoinRequestUseCase approveJoinRequestUseCase;
    private final RejectJoinRequestUseCase rejectJoinRequestUseCase;

    public JoinRequestController(
            ApplyForMembershipUseCase applyForMembershipUseCase,
            ListJoinRequestsUseCase listJoinRequestsUseCase,
            ApproveJoinRequestUseCase approveJoinRequestUseCase,
            RejectJoinRequestUseCase rejectJoinRequestUseCase) {
        this.applyForMembershipUseCase = applyForMembershipUseCase;
        this.listJoinRequestsUseCase = listJoinRequestsUseCase;
        this.approveJoinRequestUseCase = approveJoinRequestUseCase;
        this.rejectJoinRequestUseCase = rejectJoinRequestUseCase;
    }

    /** 参加申請を行う。 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public JoinRequestResponse apply(
            @PathVariable UUID circleId,
            @Valid @RequestBody ApplyForMembershipRequest request) {
        return JoinRequestResponse.from(applyForMembershipUseCase.execute(
                CircleId.of(circleId), UserId.of(request.userId())));
    }

    /** 申請一覧を取得する。status 省略時は申請中(PENDING)。 */
    @GetMapping
    public List<JoinRequestResponse> list(
            @PathVariable UUID circleId,
            @RequestParam(name = "status", defaultValue = "PENDING") JoinRequestStatus status) {
        return listJoinRequestsUseCase.execute(CircleId.of(circleId), status).stream()
                .map(JoinRequestResponse::from)
                .toList();
    }

    /** 申請を承認する(メンバーに追加)。 */
    @PostMapping("/{requestId}/approve")
    public JoinRequestResponse approve(
            @PathVariable UUID circleId,
            @PathVariable UUID requestId) {
        return JoinRequestResponse.from(approveJoinRequestUseCase.execute(
                CircleId.of(circleId), JoinRequestId.of(requestId)));
    }

    /** 申請を却下する。 */
    @PostMapping("/{requestId}/reject")
    public JoinRequestResponse reject(
            @PathVariable UUID circleId,
            @PathVariable UUID requestId) {
        return JoinRequestResponse.from(rejectJoinRequestUseCase.execute(
                CircleId.of(circleId), JoinRequestId.of(requestId)));
    }
}

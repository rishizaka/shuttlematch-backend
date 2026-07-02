package com.shuttlematch.presentation.api.controller;

import com.shuttlematch.application.usecase.session.CloseSessionUseCase;
import com.shuttlematch.application.usecase.session.CreateSessionCommand;
import com.shuttlematch.application.usecase.session.CreateSessionUseCase;
import com.shuttlematch.application.usecase.session.GetSessionUseCase;
import com.shuttlematch.application.usecase.session.ListSessionsUseCase;
import com.shuttlematch.domain.model.circle.CircleId;
import com.shuttlematch.domain.model.session.Session;
import com.shuttlematch.domain.model.session.SessionId;
import com.shuttlematch.domain.model.session.SessionStatus;
import com.shuttlematch.domain.model.user.UserId;
import com.shuttlematch.presentation.api.request.CreateSessionRequest;
import com.shuttlematch.presentation.api.response.SessionResponse;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * セッションの作成・参照・一覧を行う REST コントローラ。
 */
@RestController
public class SessionController {

    private final CreateSessionUseCase createSessionUseCase;
    private final GetSessionUseCase getSessionUseCase;
    private final ListSessionsUseCase listSessionsUseCase;
    private final CloseSessionUseCase closeSessionUseCase;

    public SessionController(
            CreateSessionUseCase createSessionUseCase,
            GetSessionUseCase getSessionUseCase,
            ListSessionsUseCase listSessionsUseCase,
            CloseSessionUseCase closeSessionUseCase) {
        this.createSessionUseCase = createSessionUseCase;
        this.getSessionUseCase = getSessionUseCase;
        this.listSessionsUseCase = listSessionsUseCase;
        this.closeSessionUseCase = closeSessionUseCase;
    }

    @PostMapping("/api/v1/circles/{circleId}/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    public SessionResponse create(
            @PathVariable UUID circleId,
            @Valid @RequestBody CreateSessionRequest request) {
        CreateSessionCommand command = new CreateSessionCommand(
                CircleId.of(circleId),
                request.title(),
                request.heldAt(),
                request.location(),
                request.capacity(),
                request.courtCount(),
                request.visibilityOrDefault(),
                UserId.of(request.createdBy()));
        Session session = createSessionUseCase.execute(command);
        return SessionResponse.from(session);
    }

    /**
     * セッション一覧を取得する。status 省略時は募集中(OPEN)を返す。
     * トップページで公開する募集中セッション一覧に用いる。
     */
    @GetMapping("/api/v1/sessions")
    public List<SessionResponse> list(
            @RequestParam(name = "status", defaultValue = "OPEN") SessionStatus status) {
        return listSessionsUseCase.execute(status).stream()
                .map(SessionResponse::from)
                .toList();
    }

    @GetMapping("/api/v1/sessions/{sessionId}")
    public SessionResponse get(@PathVariable UUID sessionId) {
        return SessionResponse.from(getSessionUseCase.execute(SessionId.of(sessionId)));
    }

    /** セッションを終了する(終了済みとして履歴に残す)。 */
    @PostMapping("/api/v1/sessions/{sessionId}/close")
    public SessionResponse close(@PathVariable UUID sessionId) {
        return SessionResponse.from(closeSessionUseCase.execute(SessionId.of(sessionId)));
    }
}

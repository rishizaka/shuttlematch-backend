package com.shuttlematch.presentation.api.controller;

import com.shuttlematch.application.usecase.session.CreateSessionCommand;
import com.shuttlematch.application.usecase.session.CreateSessionUseCase;
import com.shuttlematch.application.usecase.session.GetSessionUseCase;
import com.shuttlematch.domain.model.circle.CircleId;
import com.shuttlematch.domain.model.session.Session;
import com.shuttlematch.domain.model.session.SessionId;
import com.shuttlematch.domain.model.user.UserId;
import com.shuttlematch.presentation.api.request.CreateSessionRequest;
import com.shuttlematch.presentation.api.response.SessionResponse;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * セッションの作成・参照を行う REST コントローラ。
 */
@RestController
public class SessionController {

    private final CreateSessionUseCase createSessionUseCase;
    private final GetSessionUseCase getSessionUseCase;

    public SessionController(
            CreateSessionUseCase createSessionUseCase,
            GetSessionUseCase getSessionUseCase) {
        this.createSessionUseCase = createSessionUseCase;
        this.getSessionUseCase = getSessionUseCase;
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
                UserId.of(request.createdBy()));
        Session session = createSessionUseCase.execute(command);
        return SessionResponse.from(session);
    }

    @GetMapping("/api/v1/sessions/{sessionId}")
    public SessionResponse get(@PathVariable UUID sessionId) {
        return SessionResponse.from(getSessionUseCase.execute(SessionId.of(sessionId)));
    }
}

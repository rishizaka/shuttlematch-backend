package com.shuttlematch.presentation.api.controller;

import com.shuttlematch.application.usecase.session.AddParticipantCommand;
import com.shuttlematch.application.usecase.session.AddParticipantUseCase;
import com.shuttlematch.application.usecase.session.RemoveParticipantUseCase;
import com.shuttlematch.domain.model.session.ParticipantId;
import com.shuttlematch.domain.model.session.Session;
import com.shuttlematch.domain.model.session.SessionId;
import com.shuttlematch.domain.model.user.UserId;
import com.shuttlematch.presentation.api.request.AddParticipantRequest;
import com.shuttlematch.presentation.api.response.SessionResponse;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * セッション参加者の追加・削除を行う REST コントローラ。
 */
@RestController
@RequestMapping("/api/v1/sessions/{sessionId}/participants")
public class ParticipantController {

    private final AddParticipantUseCase addParticipantUseCase;
    private final RemoveParticipantUseCase removeParticipantUseCase;

    public ParticipantController(
            AddParticipantUseCase addParticipantUseCase,
            RemoveParticipantUseCase removeParticipantUseCase) {
        this.addParticipantUseCase = addParticipantUseCase;
        this.removeParticipantUseCase = removeParticipantUseCase;
    }

    /** 参加登録(登録ユーザーまたはゲスト)。 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SessionResponse add(
            @PathVariable UUID sessionId,
            @RequestBody AddParticipantRequest request) {
        UserId userId = request.userId() == null ? null : UserId.of(request.userId());
        AddParticipantCommand command =
                new AddParticipantCommand(SessionId.of(sessionId), userId, request.guestName());
        Session session = addParticipantUseCase.execute(command);
        return SessionResponse.from(session);
    }

    /** 参加キャンセル。 */
    @DeleteMapping("/{participantId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable UUID sessionId, @PathVariable UUID participantId) {
        removeParticipantUseCase.execute(SessionId.of(sessionId), ParticipantId.of(participantId));
    }
}

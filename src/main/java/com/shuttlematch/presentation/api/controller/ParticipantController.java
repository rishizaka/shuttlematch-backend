package com.shuttlematch.presentation.api.controller;

import com.shuttlematch.application.usecase.session.AddParticipantCommand;
import com.shuttlematch.application.usecase.session.AddParticipantUseCase;
import com.shuttlematch.application.usecase.session.MarkParticipantLeftUseCase;
import com.shuttlematch.application.usecase.session.ReactivateParticipantUseCase;
import com.shuttlematch.application.usecase.session.RemoveParticipantUseCase;
import com.shuttlematch.application.usecase.session.RenameParticipantUseCase;
import com.shuttlematch.presentation.api.request.RenameParticipantRequest;
import com.shuttlematch.domain.model.session.ParticipantId;
import com.shuttlematch.domain.model.session.Session;
import com.shuttlematch.domain.model.session.SessionId;
import com.shuttlematch.domain.model.user.UserId;
import com.shuttlematch.presentation.api.request.AddParticipantRequest;
import com.shuttlematch.presentation.api.response.SessionResponse;

import jakarta.validation.Valid;
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
    private final MarkParticipantLeftUseCase markParticipantLeftUseCase;
    private final ReactivateParticipantUseCase reactivateParticipantUseCase;
    private final RenameParticipantUseCase renameParticipantUseCase;

    public ParticipantController(
            AddParticipantUseCase addParticipantUseCase,
            RemoveParticipantUseCase removeParticipantUseCase,
            MarkParticipantLeftUseCase markParticipantLeftUseCase,
            ReactivateParticipantUseCase reactivateParticipantUseCase,
            RenameParticipantUseCase renameParticipantUseCase) {
        this.addParticipantUseCase = addParticipantUseCase;
        this.removeParticipantUseCase = removeParticipantUseCase;
        this.markParticipantLeftUseCase = markParticipantLeftUseCase;
        this.reactivateParticipantUseCase = reactivateParticipantUseCase;
        this.renameParticipantUseCase = renameParticipantUseCase;
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

    /** 参加キャンセル(生成前)。 */
    @DeleteMapping("/{participantId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable UUID sessionId, @PathVariable UUID participantId) {
        removeParticipantUseCase.execute(SessionId.of(sessionId), ParticipantId.of(participantId));
    }

    /** 早退(在席状態を LEFT に)。未開始セットの編成対象から外れる。 */
    @PostMapping("/{participantId}/leave")
    public SessionResponse leave(@PathVariable UUID sessionId, @PathVariable UUID participantId) {
        Session session = markParticipantLeftUseCase.execute(
                SessionId.of(sessionId), ParticipantId.of(participantId));
        return SessionResponse.from(session);
    }

    /** 復帰(在席状態を ACTIVE に戻す)。 */
    @PostMapping("/{participantId}/reactivate")
    public SessionResponse reactivate(@PathVariable UUID sessionId, @PathVariable UUID participantId) {
        Session session = reactivateParticipantUseCase.execute(
                SessionId.of(sessionId), ParticipantId.of(participantId));
        return SessionResponse.from(session);
    }

    /** 名前(ニックネーム)を変更する。番号のまま作った参加者に後から名前を付ける。 */
    @PostMapping("/{participantId}/rename")
    public SessionResponse rename(
            @PathVariable UUID sessionId,
            @PathVariable UUID participantId,
            @Valid @RequestBody RenameParticipantRequest request) {
        Session session = renameParticipantUseCase.execute(
                SessionId.of(sessionId), ParticipantId.of(participantId), request.name());
        return SessionResponse.from(session);
    }
}

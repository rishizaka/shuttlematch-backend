package com.shuttlematch.presentation.api.controller;

import com.shuttlematch.application.usecase.room.AddParticipantCommand;
import com.shuttlematch.application.usecase.room.AddParticipantUseCase;
import com.shuttlematch.application.usecase.room.MarkParticipantLeftUseCase;
import com.shuttlematch.application.usecase.room.ReactivateParticipantUseCase;
import com.shuttlematch.application.usecase.room.RemoveParticipantUseCase;
import com.shuttlematch.application.usecase.room.RenameParticipantUseCase;
import com.shuttlematch.presentation.api.request.RenameParticipantRequest;
import com.shuttlematch.domain.model.room.ParticipantId;
import com.shuttlematch.domain.model.room.Room;
import com.shuttlematch.domain.model.room.RoomId;
import com.shuttlematch.domain.model.user.UserId;
import com.shuttlematch.presentation.api.request.AddParticipantRequest;
import com.shuttlematch.presentation.api.response.RoomResponse;

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
@RequestMapping("/api/v1/rooms/{roomId}/participants")
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
    public RoomResponse add(
            @PathVariable UUID roomId,
            @RequestBody AddParticipantRequest request) {
        UserId userId = request.userId() == null ? null : UserId.of(request.userId());
        AddParticipantCommand command =
                new AddParticipantCommand(RoomId.of(roomId), userId, request.guestName());
        Room room = addParticipantUseCase.execute(command);
        return RoomResponse.from(room);
    }

    /** 参加キャンセル(生成前)。 */
    @DeleteMapping("/{participantId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable UUID roomId, @PathVariable UUID participantId) {
        removeParticipantUseCase.execute(RoomId.of(roomId), ParticipantId.of(participantId));
    }

    /** 早退(在席状態を LEFT に)。未開始セットの編成対象から外れる。 */
    @PostMapping("/{participantId}/leave")
    public RoomResponse leave(@PathVariable UUID roomId, @PathVariable UUID participantId) {
        Room room = markParticipantLeftUseCase.execute(
                RoomId.of(roomId), ParticipantId.of(participantId));
        return RoomResponse.from(room);
    }

    /** 復帰(在席状態を ACTIVE に戻す)。 */
    @PostMapping("/{participantId}/reactivate")
    public RoomResponse reactivate(@PathVariable UUID roomId, @PathVariable UUID participantId) {
        Room room = reactivateParticipantUseCase.execute(
                RoomId.of(roomId), ParticipantId.of(participantId));
        return RoomResponse.from(room);
    }

    /** 名前(ニックネーム)を変更する。番号のまま作った参加者に後から名前を付ける。 */
    @PostMapping("/{participantId}/rename")
    public RoomResponse rename(
            @PathVariable UUID roomId,
            @PathVariable UUID participantId,
            @Valid @RequestBody RenameParticipantRequest request) {
        Room room = renameParticipantUseCase.execute(
                RoomId.of(roomId), ParticipantId.of(participantId), request.name());
        return RoomResponse.from(room);
    }
}

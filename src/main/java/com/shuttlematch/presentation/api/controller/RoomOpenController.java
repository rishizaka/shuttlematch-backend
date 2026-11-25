package com.shuttlematch.presentation.api.controller;

import com.shuttlematch.application.usecase.room.RecordParticipantOpenUseCase;
import com.shuttlematch.domain.model.room.RoomId;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 参加者の端末で試合表が開かれたことを記録する(KPI 用)。
 * <p>
 * 運営者トークンは要求しない(開くのは参加者なので)。ルームが無ければ 404。
 */
@RestController
public class RoomOpenController {

    private final RecordParticipantOpenUseCase recordParticipantOpenUseCase;

    public RoomOpenController(RecordParticipantOpenUseCase recordParticipantOpenUseCase) {
        this.recordParticipantOpenUseCase = recordParticipantOpenUseCase;
    }

    @PostMapping("/api/v1/rooms/{roomId}/participant-opens")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void record(@PathVariable UUID roomId) {
        recordParticipantOpenUseCase.execute(RoomId.of(roomId));
    }
}

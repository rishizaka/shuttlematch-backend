package com.shuttlematch.presentation.api.controller;

import com.shuttlematch.application.usecase.room.AddFixedPairUseCase;
import com.shuttlematch.application.usecase.room.RemoveFixedPairUseCase;
import com.shuttlematch.domain.model.room.ParticipantId;
import com.shuttlematch.domain.model.room.Room;
import com.shuttlematch.domain.model.room.RoomId;
import com.shuttlematch.presentation.api.request.FixedPairRequest;
import com.shuttlematch.presentation.api.response.RoomResponse;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 固定ペア(常に同じチームで組む2人)の設定・解除を行う REST コントローラ。
 */
@RestController
@RequestMapping("/api/v1/rooms/{roomId}/fixed-pairs")
public class FixedPairController {

    private final AddFixedPairUseCase addFixedPairUseCase;
    private final RemoveFixedPairUseCase removeFixedPairUseCase;

    public FixedPairController(
            AddFixedPairUseCase addFixedPairUseCase,
            RemoveFixedPairUseCase removeFixedPairUseCase) {
        this.addFixedPairUseCase = addFixedPairUseCase;
        this.removeFixedPairUseCase = removeFixedPairUseCase;
    }

    /** 固定ペアを追加する。 */
    @PostMapping
    public RoomResponse add(
            @PathVariable UUID roomId,
            @Valid @RequestBody FixedPairRequest request) {
        Room room = addFixedPairUseCase.execute(
                RoomId.of(roomId),
                ParticipantId.of(request.participantA()),
                ParticipantId.of(request.participantB()));
        return RoomResponse.from(room);
    }

    /** 固定ペアを解除する。 */
    @DeleteMapping("/{participantA}/{participantB}")
    public RoomResponse remove(
            @PathVariable UUID roomId,
            @PathVariable UUID participantA,
            @PathVariable UUID participantB) {
        Room room = removeFixedPairUseCase.execute(
                RoomId.of(roomId),
                ParticipantId.of(participantA),
                ParticipantId.of(participantB));
        return RoomResponse.from(room);
    }
}

package com.shuttlematch.presentation.api.controller;

import com.shuttlematch.application.usecase.room.CloseRoomUseCase;
import com.shuttlematch.application.usecase.room.CreateRoomCommand;
import com.shuttlematch.application.usecase.room.CreateRoomUseCase;
import com.shuttlematch.application.usecase.room.GetRoomUseCase;
import com.shuttlematch.application.usecase.room.ListRoomsUseCase;
import com.shuttlematch.application.usecase.room.QuickCreateRoomCommand;
import com.shuttlematch.application.usecase.room.QuickCreateRoomUseCase;
import com.shuttlematch.domain.model.room.Room;
import com.shuttlematch.domain.model.room.RoomId;
import com.shuttlematch.domain.model.room.RoomStatus;
import com.shuttlematch.domain.model.user.UserId;
import com.shuttlematch.presentation.api.request.CreateRoomRequest;
import com.shuttlematch.presentation.api.request.QuickCreateRoomRequest;
import com.shuttlematch.presentation.api.response.RoomResponse;

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
public class RoomController {

    private final CreateRoomUseCase createSessionUseCase;
    private final GetRoomUseCase getRoomUseCase;
    private final ListRoomsUseCase listSessionsUseCase;
    private final CloseRoomUseCase closeSessionUseCase;
    private final QuickCreateRoomUseCase quickCreateSessionUseCase;

    public RoomController(
            CreateRoomUseCase createSessionUseCase,
            GetRoomUseCase getRoomUseCase,
            ListRoomsUseCase listSessionsUseCase,
            CloseRoomUseCase closeSessionUseCase,
            QuickCreateRoomUseCase quickCreateSessionUseCase) {
        this.createSessionUseCase = createSessionUseCase;
        this.getRoomUseCase = getRoomUseCase;
        this.listSessionsUseCase = listSessionsUseCase;
        this.closeSessionUseCase = closeSessionUseCase;
        this.quickCreateSessionUseCase = quickCreateSessionUseCase;
    }

    @PostMapping("/api/v1/rooms")
    @ResponseStatus(HttpStatus.CREATED)
    public RoomResponse create(@Valid @RequestBody CreateRoomRequest request) {
        CreateRoomCommand command = new CreateRoomCommand(
                request.title(),
                request.heldAt(),
                request.location(),
                request.capacity(),
                request.courtCount(),
                UserId.of(request.createdBy()));
        Room room = createSessionUseCase.execute(command);
        return RoomResponse.from(room);
    }

    /** かんたん作成: 参加人数・コート数・タイトルのみで、番号参加者の登録と試合表生成まで行う。 */
    @PostMapping("/api/v1/rooms/quick")
    @ResponseStatus(HttpStatus.CREATED)
    public RoomResponse quickCreate(@Valid @RequestBody QuickCreateRoomRequest request) {
        QuickCreateRoomCommand command = new QuickCreateRoomCommand(
                request.title(),
                request.courtCount(),
                request.participantCount(),
                UserId.of(request.createdBy()));
        return RoomResponse.from(quickCreateSessionUseCase.execute(command));
    }

    /**
     * ルーム一覧を取得する。status 省略時は募集中(OPEN)を返す。
     */
    @GetMapping("/api/v1/rooms")
    public List<RoomResponse> list(
            @RequestParam(name = "status", defaultValue = "OPEN") RoomStatus status) {
        return listSessionsUseCase.execute(status).stream()
                .map(RoomResponse::from)
                .toList();
    }

    @GetMapping("/api/v1/rooms/{roomId}")
    public RoomResponse get(@PathVariable UUID roomId) {
        return RoomResponse.from(getRoomUseCase.execute(RoomId.of(roomId)));
    }

    /** セッションを終了する(終了済みとして履歴に残す)。 */
    @PostMapping("/api/v1/rooms/{roomId}/close")
    public RoomResponse close(@PathVariable UUID roomId) {
        return RoomResponse.from(closeSessionUseCase.execute(RoomId.of(roomId)));
    }
}

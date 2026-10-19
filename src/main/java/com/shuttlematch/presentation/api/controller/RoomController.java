package com.shuttlematch.presentation.api.controller;

import com.shuttlematch.application.usecase.room.CloseRoomUseCase;
import com.shuttlematch.application.usecase.room.CreateRoomCommand;
import com.shuttlematch.application.usecase.room.CreateRoomUseCase;
import com.shuttlematch.application.usecase.room.DeleteRoomUseCase;
import com.shuttlematch.application.usecase.room.GetRoomByShareCodeUseCase;
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
import com.shuttlematch.presentation.api.response.PublicRoomResponse;
import com.shuttlematch.presentation.api.response.RoomResponse;

import com.shuttlematch.presentation.api.RequiresOrganizer;

import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import com.shuttlematch.infrastructure.config.AdminAccess;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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
    private final GetRoomByShareCodeUseCase getRoomByShareCodeUseCase;
    private final ListRoomsUseCase listSessionsUseCase;
    private final CloseRoomUseCase closeSessionUseCase;
    private final QuickCreateRoomUseCase quickCreateSessionUseCase;
    private final DeleteRoomUseCase deleteRoomUseCase;
    private final AdminAccess adminAccess;

    /** 管理者トークンを載せるリクエストヘッダ名。 */
    public static final String ADMIN_TOKEN_HEADER = "X-Admin-Token";

    public RoomController(
            CreateRoomUseCase createSessionUseCase,
            GetRoomUseCase getRoomUseCase,
            GetRoomByShareCodeUseCase getRoomByShareCodeUseCase,
            ListRoomsUseCase listSessionsUseCase,
            CloseRoomUseCase closeSessionUseCase,
            QuickCreateRoomUseCase quickCreateSessionUseCase,
            DeleteRoomUseCase deleteRoomUseCase,
            AdminAccess adminAccess) {
        this.createSessionUseCase = createSessionUseCase;
        this.getRoomUseCase = getRoomUseCase;
        this.getRoomByShareCodeUseCase = getRoomByShareCodeUseCase;
        this.listSessionsUseCase = listSessionsUseCase;
        this.closeSessionUseCase = closeSessionUseCase;
        this.quickCreateSessionUseCase = quickCreateSessionUseCase;
        this.deleteRoomUseCase = deleteRoomUseCase;
        this.adminAccess = adminAccess;
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
        return RoomResponse.fromCreated(room);
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
        return RoomResponse.fromCreated(quickCreateSessionUseCase.execute(command));
    }

    /**
     * ルーム一覧を取得する。条件はいずれも省略可(省略時は絞り込まない)。
     * heldFrom は開催日時がその時刻以上、heldTo は未満のルームに絞る(ISO-8601)。
     * <p>
     * 認証が無く誰でも叩けるため、返すのは「何が開催されているか」だけの
     * {@link PublicRoomResponse}。roomId や shareCode は載せない(理由はそちらの javadoc)。
     */
    @GetMapping("/api/v1/rooms")
    public List<PublicRoomResponse> list(
            @RequestParam(name = "status", required = false) RoomStatus status,
            @RequestParam(name = "heldFrom", required = false) OffsetDateTime heldFrom,
            @RequestParam(name = "heldTo", required = false) OffsetDateTime heldTo,
            @RequestHeader(name = ADMIN_TOKEN_HEADER, required = false) String adminToken) {
        // 管理者トークンを提示した運営者本人にだけ roomId を載せる。合っていなければ
        // 黙って通常の一覧を返す(エラーにしない)。不正なトークンでも「合っていない」以上の
        // 情報を与えないため。
        boolean admin = adminAccess.matches(adminToken);
        return listSessionsUseCase.execute(status, heldFrom, heldTo).stream()
                .map(room -> PublicRoomResponse.from(room, admin))
                .toList();
    }

    /**
     * 管理者トークンの確認。管理画面で貼られたトークンがその場で正しいか返すためだけにある。
     * 一致すれば 204、それ以外は 403。トークンは 256bit の乱数なので総当たりは現実的でない。
     */
    @GetMapping("/api/v1/admin/verify")
    public ResponseEntity<Void> verifyAdmin(
            @RequestHeader(name = ADMIN_TOKEN_HEADER, required = false) String adminToken) {
        return adminAccess.matches(adminToken)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    @GetMapping("/api/v1/rooms/{roomId}")
    public RoomResponse get(@PathVariable UUID roomId) {
        return RoomResponse.from(getRoomUseCase.execute(RoomId.of(roomId)));
    }

    /** 共有コードでルームを取得する(短縮URL /r/{code} の解決用)。 */
    @GetMapping("/api/v1/rooms/code/{shareCode}")
    public RoomResponse getByShareCode(@PathVariable String shareCode) {
        return RoomResponse.from(getRoomByShareCodeUseCase.execute(shareCode));
    }

    /** セッションを終了する(終了済みとして履歴に残す)。 */
    @RequiresOrganizer
    @PostMapping("/api/v1/rooms/{roomId}/close")
    public RoomResponse close(@PathVariable UUID roomId) {
        return RoomResponse.from(closeSessionUseCase.execute(RoomId.of(roomId)));
    }

    /**
     * ルームを配下データ(参加者・固定ペア・試合表)ごと完全に削除する。
     * <p>
     * 共有コードをクエリパラメータで必須にしている。認証が無いなかで
     * 「リンクを知っている人だけ」に絞るため({@link DeleteRoomUseCase} に理由を書いてある)。
     * <p>
     * 運営者トークン(X-Organizer-Token)も要求する。共有コードは参加者全員が知っているため、
     * それだけでは運営者以外の削除を防げない。
     */
    @RequiresOrganizer
    @DeleteMapping("/api/v1/rooms/{roomId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable UUID roomId,
            @RequestParam(name = "shareCode", required = false) String shareCode) {
        deleteRoomUseCase.execute(RoomId.of(roomId), shareCode);
    }
}

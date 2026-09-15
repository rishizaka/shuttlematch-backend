package com.shuttlematch.presentation.api.controller;

import com.shuttlematch.application.usecase.room.AddParticipantCommand;
import com.shuttlematch.application.usecase.room.AddParticipantUseCase;
import com.shuttlematch.application.usecase.room.ClaimNextParticipantUseCase;
import com.shuttlematch.application.usecase.room.JoinRoomUseCase;
import com.shuttlematch.application.usecase.room.MarkParticipantLeftUseCase;
import com.shuttlematch.application.usecase.room.ReactivateParticipantUseCase;
import com.shuttlematch.application.usecase.room.RemoveParticipantUseCase;
import com.shuttlematch.application.usecase.room.RenameParticipantUseCase;
import com.shuttlematch.presentation.api.request.ClaimNextParticipantRequest;
import com.shuttlematch.presentation.api.request.JoinRoomRequest;
import com.shuttlematch.presentation.api.request.ParticipantIdsRequest;
import com.shuttlematch.presentation.api.request.RenameParticipantRequest;
import com.shuttlematch.presentation.api.response.JoinResponse;
import com.shuttlematch.domain.model.room.Participant;
import com.shuttlematch.domain.model.room.ParticipantId;
import com.shuttlematch.domain.model.room.Room;
import com.shuttlematch.domain.model.room.RoomId;
import com.shuttlematch.domain.model.user.UserId;
import com.shuttlematch.presentation.api.request.AddParticipantRequest;
import com.shuttlematch.presentation.api.response.RoomResponse;

import jakarta.validation.Valid;
import java.util.List;
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
    private final JoinRoomUseCase joinRoomUseCase;
    private final RemoveParticipantUseCase removeParticipantUseCase;
    private final MarkParticipantLeftUseCase markParticipantLeftUseCase;
    private final ReactivateParticipantUseCase reactivateParticipantUseCase;
    private final RenameParticipantUseCase renameParticipantUseCase;
    private final ClaimNextParticipantUseCase claimNextParticipantUseCase;

    public ParticipantController(
            AddParticipantUseCase addParticipantUseCase,
            JoinRoomUseCase joinRoomUseCase,
            RemoveParticipantUseCase removeParticipantUseCase,
            MarkParticipantLeftUseCase markParticipantLeftUseCase,
            ReactivateParticipantUseCase reactivateParticipantUseCase,
            RenameParticipantUseCase renameParticipantUseCase,
            ClaimNextParticipantUseCase claimNextParticipantUseCase) {
        this.addParticipantUseCase = addParticipantUseCase;
        this.joinRoomUseCase = joinRoomUseCase;
        this.removeParticipantUseCase = removeParticipantUseCase;
        this.markParticipantLeftUseCase = markParticipantLeftUseCase;
        this.reactivateParticipantUseCase = reactivateParticipantUseCase;
        this.renameParticipantUseCase = renameParticipantUseCase;
        this.claimNextParticipantUseCase = claimNextParticipantUseCase;
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

    /**
     * 受付中ルームへの自己参加(名前必須)。参加順で番号が自動採番され、
     * 参加した本人の participantId と割り当て番号を返す。
     */
    @PostMapping("/join")
    @ResponseStatus(HttpStatus.CREATED)
    public JoinResponse join(
            @PathVariable UUID roomId,
            @Valid @RequestBody JoinRoomRequest request) {
        JoinRoomUseCase.Result result = joinRoomUseCase.execute(RoomId.of(roomId), request.name());
        List<Participant> participants = result.room().participants();
        int number = 0;
        for (int i = 0; i < participants.size(); i++) {
            if (participants.get(i).id().equals(result.joinedId())) {
                number = i + 1;
                break;
            }
        }
        return new JoinResponse(
                result.joinedId().value().toString(), number, RoomResponse.from(result.room()));
    }

    /**
     * 簡易作成ルームの「番号のまま」の枠に、一番若い空き番号で自動参加する(名前は任意)。
     * 同時に複数人が押しても、DB の行ロックで別々の番号が割り当てられる
     * ({@link ClaimNextParticipantUseCase} 参照)。
     */
    @PostMapping("/claim-next")
    @ResponseStatus(HttpStatus.CREATED)
    public JoinResponse claimNext(
            @PathVariable UUID roomId,
            @Valid @RequestBody ClaimNextParticipantRequest request) {
        ClaimNextParticipantUseCase.Result result =
                claimNextParticipantUseCase.execute(RoomId.of(roomId), request.name());
        List<Participant> participants = result.room().participants();
        int number = 0;
        for (int i = 0; i < participants.size(); i++) {
            if (participants.get(i).id().equals(result.claimedId())) {
                number = i + 1;
                break;
            }
        }
        return new JoinResponse(
                result.claimedId().value().toString(), number, RoomResponse.from(result.room()));
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

    /**
     * 複数の参加者をまとめて早退にする。1回の読み込み・保存で反映するため、
     * この {@code /leave} を選んだ人数ぶん並行で呼ぶより安全
     * ({@link MarkParticipantLeftUseCase} の javadoc を参照)。
     */
    @PostMapping("/leave-bulk")
    public RoomResponse leaveBulk(
            @PathVariable UUID roomId,
            @Valid @RequestBody ParticipantIdsRequest request) {
        List<ParticipantId> participantIds =
                request.participantIds().stream().map(ParticipantId::of).toList();
        Room room = markParticipantLeftUseCase.executeMany(RoomId.of(roomId), participantIds);
        return RoomResponse.from(room);
    }

    /** 復帰(在席状態を ACTIVE に戻す)。 */
    @PostMapping("/{participantId}/reactivate")
    public RoomResponse reactivate(@PathVariable UUID roomId, @PathVariable UUID participantId) {
        Room room = reactivateParticipantUseCase.execute(
                RoomId.of(roomId), ParticipantId.of(participantId));
        return RoomResponse.from(room);
    }

    /**
     * 複数の参加者をまとめて復帰(在席状態を ACTIVE に戻す)させる。
     * まとめて早退にする leave-bulk と同じ理由で、1回の読み込み・保存にまとめている。
     */
    @PostMapping("/reactivate-bulk")
    public RoomResponse reactivateBulk(
            @PathVariable UUID roomId,
            @Valid @RequestBody ParticipantIdsRequest request) {
        List<ParticipantId> participantIds =
                request.participantIds().stream().map(ParticipantId::of).toList();
        Room room = reactivateParticipantUseCase.executeMany(RoomId.of(roomId), participantIds);
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

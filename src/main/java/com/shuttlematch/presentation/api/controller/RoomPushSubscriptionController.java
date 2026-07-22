package com.shuttlematch.presentation.api.controller;

import com.shuttlematch.application.usecase.notification.SubscribeRoomPushUseCase;
import com.shuttlematch.application.usecase.notification.UnsubscribeRoomPushUseCase;
import com.shuttlematch.domain.model.notification.ExpoPushToken;
import com.shuttlematch.domain.model.notification.PushPlatform;
import com.shuttlematch.domain.model.room.ParticipantId;
import com.shuttlematch.domain.model.room.RoomId;
import com.shuttlematch.presentation.api.request.RoomPushSubscriptionRequest;

import jakarta.validation.Valid;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * ルームの Push 通知の購読・解除を行う REST コントローラ。
 *
 * <p>宛先をユーザーではなくルーム購読で持つのは、参加者のほとんどが user_id を持たない
 * ゲスト(番号だけの参加者)で、ユーザー単位では宛先を解決できないため。
 */
@RestController
@RequestMapping("/api/v1/rooms/{roomId}/push-subscriptions")
public class RoomPushSubscriptionController {

    private final SubscribeRoomPushUseCase subscribeRoomPushUseCase;
    private final UnsubscribeRoomPushUseCase unsubscribeRoomPushUseCase;

    public RoomPushSubscriptionController(
            SubscribeRoomPushUseCase subscribeRoomPushUseCase,
            UnsubscribeRoomPushUseCase unsubscribeRoomPushUseCase) {
        this.subscribeRoomPushUseCase = subscribeRoomPushUseCase;
        this.unsubscribeRoomPushUseCase = unsubscribeRoomPushUseCase;
    }

    /** 購読する(再登録は上書き)。ルーム画面を開いたとき・自分の番号を設定したときに呼ぶ。 */
    @PutMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void subscribe(
            @PathVariable UUID roomId,
            @Valid @RequestBody RoomPushSubscriptionRequest request) {
        subscribeRoomPushUseCase.execute(
                RoomId.of(roomId),
                ExpoPushToken.of(request.expoToken()),
                Optional.ofNullable(request.participantId()).map(ParticipantId::of),
                PushPlatform.valueOf(request.platform().toUpperCase(Locale.ROOT)));
    }

    /**
     * 購読を解除する。トークンは "ExponentPushToken[...]" と角括弧を含むため、
     * パス変数ではなくクエリパラメータで受ける(URL エンコードの取り回しを避ける)。
     */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsubscribe(@PathVariable UUID roomId, @RequestParam("expoToken") String expoToken) {
        unsubscribeRoomPushUseCase.execute(RoomId.of(roomId), ExpoPushToken.of(expoToken));
    }
}

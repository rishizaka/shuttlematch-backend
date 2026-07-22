package com.shuttlematch.presentation.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shuttlematch.application.usecase.notification.SubscribeRoomPushUseCase;
import com.shuttlematch.application.usecase.notification.UnsubscribeRoomPushUseCase;
import com.shuttlematch.domain.model.notification.ExpoPushToken;
import com.shuttlematch.domain.model.notification.PushPlatform;
import com.shuttlematch.domain.model.room.ParticipantId;
import com.shuttlematch.domain.model.room.RoomId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RoomPushSubscriptionController.class)
class RoomPushSubscriptionControllerTest {

    private static final String TOKEN = "ExponentPushToken[aaaaaaaaaaaaaaaaaaaaaa]";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SubscribeRoomPushUseCase subscribeRoomPushUseCase;

    @MockitoBean
    private UnsubscribeRoomPushUseCase unsubscribeRoomPushUseCase;

    @Test
    @DisplayName("PUT で購読を登録する(自分の番号つき)")
    void subscribeWithParticipant() throws Exception {
        UUID roomId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();

        mockMvc.perform(put("/api/v1/rooms/{roomId}/push-subscriptions", roomId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expoToken":"%s","participantId":"%s","platform":"android"}
                                """.formatted(TOKEN, participantId)))
                .andExpect(status().isNoContent());

        verify(subscribeRoomPushUseCase).execute(
                eq(RoomId.of(roomId)),
                eq(ExpoPushToken.of(TOKEN)),
                eq(Optional.of(ParticipantId.of(participantId))),
                eq(PushPlatform.ANDROID));
    }

    @Test
    @DisplayName("自分の番号は任意(未設定でも登録できる)")
    void subscribeWithoutParticipant() throws Exception {
        UUID roomId = UUID.randomUUID();

        mockMvc.perform(put("/api/v1/rooms/{roomId}/push-subscriptions", roomId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expoToken":"%s","platform":"ios"}
                                """.formatted(TOKEN)))
                .andExpect(status().isNoContent());

        verify(subscribeRoomPushUseCase).execute(
                eq(RoomId.of(roomId)),
                eq(ExpoPushToken.of(TOKEN)),
                eq(Optional.empty()),
                eq(PushPlatform.IOS));
    }

    @Test
    @DisplayName("platform が ios/android 以外なら 400")
    void rejectsUnknownPlatform() throws Exception {
        mockMvc.perform(put("/api/v1/rooms/{roomId}/push-subscriptions", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expoToken":"%s","platform":"windows"}
                                """.formatted(TOKEN)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Expo の形式でないトークンは 400")
    void rejectsInvalidToken() throws Exception {
        mockMvc.perform(put("/api/v1/rooms/{roomId}/push-subscriptions", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expoToken":"fcm-raw-token","platform":"android"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("DELETE で購読を解除する(トークンはクエリパラメータ)")
    void unsubscribe() throws Exception {
        UUID roomId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/rooms/{roomId}/push-subscriptions", roomId)
                        .param("expoToken", TOKEN))
                .andExpect(status().isNoContent());

        verify(unsubscribeRoomPushUseCase).execute(RoomId.of(roomId), ExpoPushToken.of(TOKEN));
    }
}

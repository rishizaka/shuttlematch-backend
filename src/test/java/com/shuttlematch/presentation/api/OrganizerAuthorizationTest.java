package com.shuttlematch.presentation.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shuttlematch.application.OrganizerForbiddenException;
import com.shuttlematch.application.usecase.room.VerifyOrganizerUseCase;
import com.shuttlematch.domain.model.room.Room;
import com.shuttlematch.domain.model.room.RoomId;
import com.shuttlematch.domain.model.user.UserId;
import com.shuttlematch.presentation.api.controller.ParticipantController;
import com.shuttlematch.presentation.api.controller.RoomController;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 運営者トークンによる認可の振る舞い。
 * <p>
 * 試合表・参加者構成を変える運営操作(終了・削除・参加者の追加/削除・早退/復帰・試合表操作)は
 * 運営者だけ、参加者本人も使う操作(番号を名乗る rename・「参加する」)と参照は誰でも、
 * という切り分けを固定する。
 */
@WebMvcTest({RoomController.class, ParticipantController.class})
class OrganizerAuthorizationTest {

    private static final UUID ROOM_ID = UUID.randomUUID();
    private static final UUID PARTICIPANT_ID = UUID.randomUUID();
    private static final String TOKEN = "test-organizer-token";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VerifyOrganizerUseCase verifyOrganizerUseCase;

    @MockitoBean
    private com.shuttlematch.application.usecase.room.CreateRoomUseCase createRoomUseCase;
    @MockitoBean
    private com.shuttlematch.application.usecase.room.GetRoomUseCase getRoomUseCase;
    @MockitoBean
    private com.shuttlematch.application.usecase.room.GetRoomByShareCodeUseCase getRoomByShareCodeUseCase;
    @MockitoBean
    private com.shuttlematch.application.usecase.room.ListRoomsUseCase listRoomsUseCase;
    @MockitoBean
    private com.shuttlematch.application.usecase.room.CloseRoomUseCase closeRoomUseCase;
    @MockitoBean
    private com.shuttlematch.application.usecase.room.QuickCreateRoomUseCase quickCreateRoomUseCase;
    @MockitoBean
    private com.shuttlematch.application.usecase.room.DeleteRoomUseCase deleteRoomUseCase;
    @MockitoBean
    private com.shuttlematch.application.usecase.room.AddParticipantUseCase addParticipantUseCase;
    @MockitoBean
    private com.shuttlematch.application.usecase.room.JoinRoomUseCase joinRoomUseCase;
    @MockitoBean
    private com.shuttlematch.application.usecase.room.RemoveParticipantUseCase removeParticipantUseCase;
    @MockitoBean
    private com.shuttlematch.application.usecase.room.MarkParticipantLeftUseCase markParticipantLeftUseCase;
    @MockitoBean
    private com.shuttlematch.application.usecase.room.ReactivateParticipantUseCase reactivateParticipantUseCase;
    @MockitoBean
    private com.shuttlematch.application.usecase.room.RenameParticipantUseCase renameParticipantUseCase;
    @MockitoBean
    private com.shuttlematch.application.usecase.room.ClaimNextParticipantUseCase claimNextParticipantUseCase;
    // RoomController が一覧の管理者表示に使う。ここでは一覧を扱わないのでモックで足りる。
    @MockitoBean
    private com.shuttlematch.infrastructure.config.AdminAccess adminAccess;

    private Room sampleRoom() {
        return Room.create("練習会", OffsetDateTime.now(), null, null, UserId.of(UUID.randomUUID()));
    }

    @Test
    @DisplayName("運営者専用の操作は X-Organizer-Token の値をそのまま検証に渡す")
    void passesHeaderToVerification() throws Exception {
        when(closeRoomUseCase.execute(any())).thenReturn(sampleRoom());

        mockMvc.perform(post("/api/v1/rooms/{roomId}/close", ROOM_ID)
                        .header(OrganizerAuthInterceptor.HEADER, TOKEN))
                .andExpect(status().isOk());

        verify(verifyOrganizerUseCase).execute(eq(RoomId.of(ROOM_ID)), eq(TOKEN));
    }

    @Test
    @DisplayName("トークンが不正なら 403 になり、ユースケースは呼ばれない")
    void rejectsWithForbidden() throws Exception {
        doThrow(new OrganizerForbiddenException("この操作はルームを作成した端末からのみ実行できます"))
                .when(verifyOrganizerUseCase).execute(any(), any());

        mockMvc.perform(delete("/api/v1/rooms/{roomId}", ROOM_ID)
                        .param("shareCode", "abcd2345")
                        .header(OrganizerAuthInterceptor.HEADER, "wrong"))
                .andExpect(status().isForbidden());

        verify(deleteRoomUseCase, never()).execute(any(), any());
    }

    @Test
    @DisplayName("運営者トークンの確認: 合っていれば 204")
    void organizerCheckPasses() throws Exception {
        mockMvc.perform(get("/api/v1/rooms/{roomId}/organizer-check", ROOM_ID)
                        .header(OrganizerAuthInterceptor.HEADER, TOKEN))
                .andExpect(status().isNoContent());

        verify(verifyOrganizerUseCase).execute(eq(RoomId.of(ROOM_ID)), eq(TOKEN));
    }

    @Test
    @DisplayName("運営者トークンの確認: 違えば 403")
    void organizerCheckRejects() throws Exception {
        doThrow(new OrganizerForbiddenException("運営者のみ"))
                .when(verifyOrganizerUseCase).execute(any(), any());

        mockMvc.perform(get("/api/v1/rooms/{roomId}/organizer-check", ROOM_ID)
                        .header(OrganizerAuthInterceptor.HEADER, "wrong"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ヘッダが無くても検証は呼ばれる(null を渡して弾かせる)")
    void verifiesEvenWithoutHeader() throws Exception {
        doThrow(new OrganizerForbiddenException("トークンがありません"))
                .when(verifyOrganizerUseCase).execute(any(), any());

        mockMvc.perform(post("/api/v1/rooms/{roomId}/close", ROOM_ID))
                .andExpect(status().isForbidden());

        verify(verifyOrganizerUseCase).execute(eq(RoomId.of(ROOM_ID)), eq(null));
    }

    @Test
    @DisplayName("早退は運営者だけ(未開始セットを自動で組み直すので試合表を書き換える操作として守る)")
    void leaveRequiresOrganizer() throws Exception {
        doThrow(new OrganizerForbiddenException("運営者のみ"))
                .when(verifyOrganizerUseCase).execute(any(), any());

        mockMvc.perform(post("/api/v1/rooms/{roomId}/participants/{participantId}/leave",
                        ROOM_ID, PARTICIPANT_ID))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/rooms/{roomId}/participants/leave-bulk", ROOM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"participantIds\":[\"%s\"]}".formatted(PARTICIPANT_ID)))
                .andExpect(status().isForbidden());

        verify(markParticipantLeftUseCase, never()).execute(any(), any());
    }

    @Test
    @DisplayName("復帰も運営者だけ(一括も同じ)")
    void reactivateRequiresOrganizer() throws Exception {
        doThrow(new OrganizerForbiddenException("運営者のみ"))
                .when(verifyOrganizerUseCase).execute(any(), any());

        mockMvc.perform(post("/api/v1/rooms/{roomId}/participants/{participantId}/reactivate",
                        ROOM_ID, PARTICIPANT_ID))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/rooms/{roomId}/participants/reactivate-bulk", ROOM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"participantIds\":[\"%s\"]}".formatted(PARTICIPANT_ID)))
                .andExpect(status().isForbidden());

        verify(reactivateParticipantUseCase, never()).execute(any(), any());
    }

    @Test
    @DisplayName("名前の変更はトークン不要(参加者本人が番号を名乗る・自分の番号をフリーにするのに使う)")
    void renameDoesNotRequireOrganizer() throws Exception {
        when(renameParticipantUseCase.execute(any(), any(), any())).thenReturn(sampleRoom());

        mockMvc.perform(post("/api/v1/rooms/{roomId}/participants/{participantId}/rename",
                        ROOM_ID, PARTICIPANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"たろう\"}"))
                .andExpect(status().isOk());

        verify(verifyOrganizerUseCase, never()).execute(any(), any());
    }

    @Test
    @DisplayName("「参加する」(空き番号への自動参加)はトークン不要")
    void claimNextDoesNotRequireOrganizer() throws Exception {
        Room room = sampleRoom();
        var claimed = room.addGuest("1");
        when(claimNextParticipantUseCase.execute(any(), any()))
                .thenReturn(new com.shuttlematch.application.usecase.room.ClaimNextParticipantUseCase
                        .Result(room, claimed.id()));

        mockMvc.perform(post("/api/v1/rooms/{roomId}/participants/claim-next", ROOM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"たろう\"}"))
                .andExpect(status().isCreated());

        verify(verifyOrganizerUseCase, never()).execute(any(), any());
    }

    @Test
    @DisplayName("参照はトークン不要(共有リンクで誰でも試合表を見られる)")
    void readDoesNotRequireOrganizer() throws Exception {
        when(getRoomUseCase.execute(any())).thenReturn(sampleRoom());

        mockMvc.perform(get("/api/v1/rooms/{roomId}", ROOM_ID))
                .andExpect(status().isOk());

        verify(verifyOrganizerUseCase, never()).execute(any(), any());
    }

    @Test
    @DisplayName("作成レスポンスにだけ運営者トークンの平文が含まれる")
    void createReturnsTokenOnce() throws Exception {
        Room created = sampleRoom();
        when(quickCreateRoomUseCase.execute(any())).thenReturn(created);

        mockMvc.perform(post("/api/v1/rooms/quick")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"練習会","courtCount":2,"participantCount":8,
                                 "createdBy":"%s"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.organizerToken").value(created.issuedOrganizerToken()));
    }

    @Test
    @DisplayName("参照レスポンスには運営者トークンを含めない(共有リンクで全員が運営者になれてしまう)")
    void readNeverLeaksToken() throws Exception {
        when(getRoomUseCase.execute(any())).thenReturn(sampleRoom());

        mockMvc.perform(get("/api/v1/rooms/{roomId}", ROOM_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizerToken").doesNotExist());
    }

    @Test
    @DisplayName("共有コードでの参照にも運営者トークンを含めない")
    void shareCodeLookupNeverLeaksToken() throws Exception {
        when(getRoomByShareCodeUseCase.execute(any())).thenReturn(sampleRoom());

        mockMvc.perform(get("/api/v1/rooms/code/{shareCode}", "abcd2345"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizerToken").doesNotExist());
    }
}

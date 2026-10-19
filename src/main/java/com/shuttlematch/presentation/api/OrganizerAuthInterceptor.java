package com.shuttlematch.presentation.api;

import com.shuttlematch.application.OrganizerForbiddenException;
import com.shuttlematch.application.usecase.room.VerifyOrganizerUseCase;
import com.shuttlematch.domain.model.room.RoomId;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

/**
 * {@link RequiresOrganizer} が付いたハンドラの手前で運営者トークンを検証する。
 * <p>
 * ログイン認証ではなく「そのルームを作成した端末か」だけを見る。トークンはルーム作成時に
 * 一度だけ払い出され、クライアントが端末ローカルに保存して {@code X-Organizer-Token} で提示する。
 */
@Component
public class OrganizerAuthInterceptor implements HandlerInterceptor {

    /** 運営者トークンを載せるリクエストヘッダ名。 */
    public static final String HEADER = "X-Organizer-Token";

    private final VerifyOrganizerUseCase verifyOrganizerUseCase;

    public OrganizerAuthInterceptor(VerifyOrganizerUseCase verifyOrganizerUseCase) {
        this.verifyOrganizerUseCase = verifyOrganizerUseCase;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler) {
        // CORS プリフライト(OPTIONS)は HandlerMethod ではないのでここを素通りする。
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        if (!handlerMethod.hasMethodAnnotation(RequiresOrganizer.class)) {
            return true;
        }
        verifyOrganizerUseCase.execute(resolveRoomId(request), request.getHeader(HEADER));
        return true;
    }

    /** パス変数 {@code roomId} を取り出す。@RequiresOrganizer は必ず roomId を含むパスに付く。 */
    private RoomId resolveRoomId(HttpServletRequest request) {
        Object attribute = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        String roomId = attribute instanceof Map<?, ?> variables
                ? String.valueOf(variables.get("roomId"))
                : null;
        if (roomId == null || "null".equals(roomId)) {
            // 付ける場所を間違えた場合。開けっ放しにせず落とす(fail-closed)。
            throw new IllegalStateException(
                    "@RequiresOrganizer は roomId を含むパスにのみ付けられます: "
                            + request.getRequestURI());
        }
        try {
            return RoomId.of(UUID.fromString(roomId));
        } catch (IllegalArgumentException e) {
            throw new OrganizerForbiddenException("ルームIDの形式が不正です");
        }
    }
}

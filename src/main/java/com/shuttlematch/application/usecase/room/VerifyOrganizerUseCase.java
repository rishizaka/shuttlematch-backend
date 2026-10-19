package com.shuttlematch.application.usecase.room;

import com.shuttlematch.application.OrganizerForbiddenException;
import com.shuttlematch.application.ResourceNotFoundException;
import com.shuttlematch.domain.model.room.Room;
import com.shuttlematch.domain.model.room.RoomId;
import com.shuttlematch.domain.repository.RoomRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 提示された運営者トークンが、そのルームの運営者のものか検証するユースケース。
 * 運営者専用の操作を実行する前段で呼ばれる。
 */
@Service
public class VerifyOrganizerUseCase {

    private final RoomRepository roomRepository;

    public VerifyOrganizerUseCase(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    /**
     * 検証する。運営者でなければ {@link OrganizerForbiddenException}、
     * ルームが存在しなければ {@link ResourceNotFoundException}。
     */
    @Transactional(readOnly = true)
    public void execute(RoomId roomId, String plainToken) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("ルームが見つかりません: " + roomId.value()));
        if (!room.isOrganizer(plainToken)) {
            throw new OrganizerForbiddenException(
                    "この操作はルームを作成した端末からのみ実行できます");
        }
    }
}

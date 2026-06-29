package com.shuttlematch.application.usecase.session;

import com.shuttlematch.application.ResourceNotFoundException;
import com.shuttlematch.domain.model.session.Session;
import com.shuttlematch.domain.repository.SessionRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * セッションに参加者(登録ユーザーまたはゲスト)を追加するユースケース。
 */
@Service
public class AddParticipantUseCase {

    private final SessionRepository sessionRepository;

    public AddParticipantUseCase(SessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    @Transactional
    public Session execute(AddParticipantCommand command) {
        Session session = sessionRepository.findById(command.sessionId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "セッションが見つかりません: " + command.sessionId().value()));

        if (command.isGuest()) {
            session.addGuest(command.guestName());
        } else {
            session.addUser(command.userId());
        }
        return sessionRepository.save(session);
    }
}

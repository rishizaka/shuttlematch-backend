package com.shuttlematch.application.usecase.session;

import com.shuttlematch.application.ResourceNotFoundException;
import com.shuttlematch.domain.model.session.ParticipantId;
import com.shuttlematch.domain.model.session.Session;
import com.shuttlematch.domain.model.session.SessionId;
import com.shuttlematch.domain.repository.SessionRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * セッションから参加者を削除する(参加キャンセル)ユースケース。
 */
@Service
public class RemoveParticipantUseCase {

    private final SessionRepository sessionRepository;

    public RemoveParticipantUseCase(SessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    @Transactional
    public void execute(SessionId sessionId, ParticipantId participantId) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "セッションが見つかりません: " + sessionId.value()));

        boolean removed = session.removeParticipant(participantId);
        if (!removed) {
            throw new ResourceNotFoundException(
                    "参加者が見つかりません: " + participantId.value());
        }
        sessionRepository.save(session);
    }
}

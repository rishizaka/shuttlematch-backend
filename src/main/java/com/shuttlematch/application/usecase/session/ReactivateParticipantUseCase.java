package com.shuttlematch.application.usecase.session;

import com.shuttlematch.application.ResourceNotFoundException;
import com.shuttlematch.domain.model.session.ParticipantId;
import com.shuttlematch.domain.model.session.Session;
import com.shuttlematch.domain.model.session.SessionId;
import com.shuttlematch.domain.repository.SessionRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 早退した参加者を在席(ACTIVE)に戻す。次回の再編成から編成対象に戻る。
 */
@Service
public class ReactivateParticipantUseCase {

    private final SessionRepository sessionRepository;

    public ReactivateParticipantUseCase(SessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    @Transactional
    public Session execute(SessionId sessionId, ParticipantId participantId) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "セッションが見つかりません: " + sessionId.value()));

        if (!session.reactivateParticipant(participantId)) {
            throw new ResourceNotFoundException("参加者が見つかりません: " + participantId.value());
        }
        return sessionRepository.save(session);
    }
}

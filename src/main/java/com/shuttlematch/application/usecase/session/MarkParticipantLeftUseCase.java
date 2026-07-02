package com.shuttlematch.application.usecase.session;

import com.shuttlematch.application.ResourceNotFoundException;
import com.shuttlematch.domain.model.session.ParticipantId;
import com.shuttlematch.domain.model.session.Session;
import com.shuttlematch.domain.model.session.SessionId;
import com.shuttlematch.domain.repository.SessionRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 参加者を早退にする(在席状態を LEFT に更新)。履歴は残り、未開始セットの編成対象から外れる。
 */
@Service
public class MarkParticipantLeftUseCase {

    private final SessionRepository sessionRepository;

    public MarkParticipantLeftUseCase(SessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    @Transactional
    public Session execute(SessionId sessionId, ParticipantId participantId) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "セッションが見つかりません: " + sessionId.value()));

        if (!session.markParticipantLeft(participantId)) {
            throw new ResourceNotFoundException("参加者が見つかりません: " + participantId.value());
        }
        return sessionRepository.save(session);
    }
}

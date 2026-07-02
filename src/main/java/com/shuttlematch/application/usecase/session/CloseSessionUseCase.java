package com.shuttlematch.application.usecase.session;

import com.shuttlematch.application.ResourceNotFoundException;
import com.shuttlematch.domain.model.session.Session;
import com.shuttlematch.domain.model.session.SessionId;
import com.shuttlematch.domain.repository.SessionRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * セッションを終了する(履歴として残す)ユースケース。試合表はそのまま保持される。
 */
@Service
public class CloseSessionUseCase {

    private final SessionRepository sessionRepository;

    public CloseSessionUseCase(SessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    @Transactional
    public Session execute(SessionId sessionId) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "セッションが見つかりません: " + sessionId.value()));

        session.close();
        return sessionRepository.save(session);
    }
}

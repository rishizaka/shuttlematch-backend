package com.shuttlematch.application.usecase.session;

import com.shuttlematch.application.ResourceNotFoundException;
import com.shuttlematch.domain.model.session.ParticipantId;
import com.shuttlematch.domain.model.session.Session;
import com.shuttlematch.domain.model.session.SessionId;
import com.shuttlematch.domain.repository.SessionRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ゲスト参加者の名前(ニックネーム)を変更するユースケース。番号のまま作った参加者に後から名前を付ける。
 */
@Service
public class RenameParticipantUseCase {

    private final SessionRepository sessionRepository;

    public RenameParticipantUseCase(SessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    @Transactional
    public Session execute(SessionId sessionId, ParticipantId participantId, String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("名前を入力してください");
        }
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "セッションが見つかりません: " + sessionId.value()));

        if (!session.renameParticipant(participantId, name.trim())) {
            throw new ResourceNotFoundException("参加者が見つかりません: " + participantId.value());
        }
        return sessionRepository.save(session);
    }
}

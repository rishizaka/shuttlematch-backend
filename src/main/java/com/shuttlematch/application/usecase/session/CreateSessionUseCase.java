package com.shuttlematch.application.usecase.session;

import com.shuttlematch.domain.model.session.Session;
import com.shuttlematch.domain.repository.SessionRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * セッションを作成するユースケース。
 */
@Service
public class CreateSessionUseCase {

    private final SessionRepository sessionRepository;

    public CreateSessionUseCase(SessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    @Transactional
    public Session execute(CreateSessionCommand command) {
        Session session = Session.create(
                command.title(),
                command.heldAt(),
                command.location(),
                command.capacity(),
                command.courtCount(),
                command.createdBy());
        return sessionRepository.save(session);
    }
}

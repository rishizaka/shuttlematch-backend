package com.shuttlematch.application.usecase.session;

import com.shuttlematch.domain.model.session.Session;
import com.shuttlematch.domain.model.session.SessionStatus;
import com.shuttlematch.domain.repository.SessionRepository;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 指定ステータスのセッション一覧を取得するユースケース。
 * 募集中(OPEN)のセッションをトップページで公開する用途に用いる。
 */
@Service
public class ListSessionsUseCase {

    private final SessionRepository sessionRepository;

    public ListSessionsUseCase(SessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    @Transactional(readOnly = true)
    public List<Session> execute(SessionStatus status) {
        return sessionRepository.findByStatus(status);
    }
}

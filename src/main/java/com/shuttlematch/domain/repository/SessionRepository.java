package com.shuttlematch.domain.repository;

import com.shuttlematch.domain.model.session.Session;
import com.shuttlematch.domain.model.session.SessionId;

import java.util.Optional;

/**
 * セッション集約の永続化を担うリポジトリ(ドメイン層のインターフェース)。
 */
public interface SessionRepository {

    Session save(Session session);

    Optional<Session> findById(SessionId sessionId);
}

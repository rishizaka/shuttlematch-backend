package com.shuttlematch.domain.repository;

import com.shuttlematch.domain.model.session.Session;
import com.shuttlematch.domain.model.session.SessionId;
import com.shuttlematch.domain.model.session.SessionStatus;

import java.util.List;
import java.util.Optional;

/**
 * セッション集約の永続化を担うリポジトリ(ドメイン層のインターフェース)。
 */
public interface SessionRepository {

    Session save(Session session);

    Optional<Session> findById(SessionId sessionId);

    /** 指定ステータスのセッションを開催日時の昇順で取得する。 */
    List<Session> findByStatus(SessionStatus status);
}

package com.shuttlematch.domain.repository;

import com.shuttlematch.domain.model.session.ParticipantId;
import com.shuttlematch.domain.model.session.SessionId;

import java.util.List;

/**
 * セッション参加者の参照を担うリポジトリ(ドメイン層のインターフェース)。
 * <p>
 * 本来は Session 集約の一部だが、現時点では試合生成に必要な参加者 ID の
 * 取得に絞ったインターフェースとして定義する。Session 集約の実装時に統合を検討する。
 */
public interface SessionParticipantRepository {

    /** 指定セッションの参加者 ID 一覧を返す(参加していなければ空)。 */
    List<ParticipantId> findParticipantIds(SessionId sessionId);
}

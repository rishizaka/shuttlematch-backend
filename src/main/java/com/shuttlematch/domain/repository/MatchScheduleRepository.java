package com.shuttlematch.domain.repository;

import com.shuttlematch.domain.model.match.MatchSchedule;
import com.shuttlematch.domain.model.session.SessionId;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * 試合スケジュール集約の永続化を担うリポジトリ(ドメイン層のインターフェース)。
 * 実装はインフラ層に置く。
 */
public interface MatchScheduleRepository {

    MatchSchedule save(MatchSchedule schedule);

    Optional<MatchSchedule> findBySessionId(SessionId sessionId);

    /** 再生成のため既存スケジュールを削除する。 */
    void deleteBySessionId(SessionId sessionId);

    /**
     * 指定試合のセット開始時刻を記録し、更新後のスケジュールを返す。
     * スケジュールや該当試合番号が無ければ空を返す。
     */
    Optional<MatchSchedule> startMatch(SessionId sessionId, int matchNumber, OffsetDateTime startedAt);
}

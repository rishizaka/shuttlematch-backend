package com.shuttlematch.infrastructure.persistence.jpa;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameScoreJpaRepository extends JpaRepository<GameScoreEntity, UUID> {

    /** 順位順(スコア降順・同点は記録が早い順)に上位を返す。 */
    List<GameScoreEntity> findByGameOrderByScoreDescRecordedAtAsc(String game, Limit limit);

    /**
     * 順位順に全件返す。記録のたびに 6 位以下を切り捨てるので、1 ゲームあたり数行しかない。
     * 切り捨て対象を決めるために使う。
     */
    List<GameScoreEntity> findByGameOrderByScoreDescRecordedAtAsc(String game);
}

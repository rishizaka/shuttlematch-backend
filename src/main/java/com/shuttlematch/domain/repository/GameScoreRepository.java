package com.shuttlematch.domain.repository;

import com.shuttlematch.domain.model.game.GameScore;
import com.shuttlematch.domain.model.game.MiniGame;

import java.util.List;

/**
 * ミニゲームのランキングの永続化を担うリポジトリ(ドメイン層のインターフェース)。
 */
public interface GameScoreRepository {

    /** 上位 {@code limit} 件を順位順(スコア降順・同点は記録が早い順)で返す。 */
    List<GameScore> findTop(MiniGame game, int limit);

    /** スコアを1件記録する。 */
    void save(GameScore score);

    /**
     * 上位 {@code keep} 件だけ残し、それより下を削除する。
     * ランキングは上位しか見せないので、記録するたびに切り捨てて行を増やさない。
     */
    void pruneBeyond(MiniGame game, int keep);
}

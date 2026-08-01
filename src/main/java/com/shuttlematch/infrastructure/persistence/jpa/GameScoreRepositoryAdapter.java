package com.shuttlematch.infrastructure.persistence.jpa;

import com.shuttlematch.domain.model.game.GameScore;
import com.shuttlematch.domain.model.game.MiniGame;
import com.shuttlematch.domain.model.game.PlayerName;
import com.shuttlematch.domain.repository.GameScoreRepository;

import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link GameScoreRepository} の JPA 実装。
 */
@Repository
public class GameScoreRepositoryAdapter implements GameScoreRepository {

    private final GameScoreJpaRepository jpaRepository;

    public GameScoreRepositoryAdapter(GameScoreJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public List<GameScore> findTop(MiniGame game, int limit) {
        return jpaRepository
                .findByGameOrderByScoreDescRecordedAtAsc(game.slug(), Limit.of(limit)).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public void save(GameScore score) {
        GameScoreEntity entity = new GameScoreEntity();
        entity.setId(UUID.randomUUID());
        entity.setGame(score.game().slug());
        entity.setPlayerName(score.playerName().value());
        entity.setScore(score.score());
        entity.setRecordedAt(score.recordedAt().atOffset(ZoneOffset.UTC));
        jpaRepository.save(entity);
    }

    @Override
    @Transactional
    public void pruneBeyond(MiniGame game, int keep) {
        List<GameScoreEntity> ordered =
                jpaRepository.findByGameOrderByScoreDescRecordedAtAsc(game.slug());
        if (ordered.size() <= keep) {
            return;
        }
        jpaRepository.deleteAll(ordered.subList(keep, ordered.size()));
    }

    private GameScore toDomain(GameScoreEntity entity) {
        return new GameScore(
                MiniGame.fromSlug(entity.getGame()),
                PlayerName.of(entity.getPlayerName()),
                entity.getScore(),
                entity.getRecordedAt().toInstant());
    }
}

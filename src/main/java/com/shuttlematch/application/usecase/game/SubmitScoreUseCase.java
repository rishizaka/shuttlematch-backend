package com.shuttlematch.application.usecase.game;

import com.shuttlematch.domain.model.game.GameScore;
import com.shuttlematch.domain.model.game.MiniGame;
import com.shuttlematch.domain.model.game.PlayerName;
import com.shuttlematch.domain.model.game.Ranking;
import com.shuttlematch.domain.repository.GameScoreRepository;

import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ミニゲームのスコアをランキングに登録するユースケース。
 *
 * <p>ランクインしないスコアは保存しない(上位5件しか見せないので持つ意味がない)。
 * 登録したら6位以下を切り捨て、テーブルが増え続けないようにする。
 */
@Service
public class SubmitScoreUseCase {

    private final GameScoreRepository gameScoreRepository;
    private final Clock clock;

    public SubmitScoreUseCase(GameScoreRepository gameScoreRepository, Clock clock) {
        this.gameScoreRepository = gameScoreRepository;
        this.clock = clock;
    }

    @Transactional
    public SubmitScoreResult execute(MiniGame game, PlayerName playerName, int score) {
        Ranking current = new Ranking(game, gameScoreRepository.findTop(game, Ranking.SIZE));
        Optional<Integer> rank = current.rankFor(score);
        if (rank.isEmpty()) {
            return new SubmitScoreResult(Optional.empty(), current);
        }
        gameScoreRepository.save(new GameScore(game, playerName, score, clock.instant()));
        gameScoreRepository.pruneBeyond(game, Ranking.SIZE);
        Ranking updated = new Ranking(game, gameScoreRepository.findTop(game, Ranking.SIZE));
        return new SubmitScoreResult(rank, updated);
    }
}

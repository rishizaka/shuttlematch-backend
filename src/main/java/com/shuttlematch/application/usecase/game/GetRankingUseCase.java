package com.shuttlematch.application.usecase.game;

import com.shuttlematch.domain.model.game.MiniGame;
import com.shuttlematch.domain.model.game.Ranking;
import com.shuttlematch.domain.repository.GameScoreRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ミニゲームのランキング(上位5件)を取得するユースケース。
 */
@Service
public class GetRankingUseCase {

    private final GameScoreRepository gameScoreRepository;

    public GetRankingUseCase(GameScoreRepository gameScoreRepository) {
        this.gameScoreRepository = gameScoreRepository;
    }

    @Transactional(readOnly = true)
    public Ranking execute(MiniGame game) {
        return new Ranking(game, gameScoreRepository.findTop(game, Ranking.SIZE));
    }
}

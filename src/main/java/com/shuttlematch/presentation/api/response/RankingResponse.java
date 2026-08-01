package com.shuttlematch.presentation.api.response;

import com.shuttlematch.domain.model.game.GameScore;
import com.shuttlematch.domain.model.game.Ranking;

import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

/**
 * ミニゲームのランキング(上位5件)のレスポンス。
 *
 * @param game    ミニゲームのスラッグ(flap / rain / coin / flick)
 * @param entries 順位順のエントリ
 */
public record RankingResponse(String game, List<Entry> entries) {

    /**
     * ランキングの1行。
     *
     * @param rank       順位(1始まり)
     * @param playerName プレイヤー名
     * @param score      スコア
     * @param recordedAt 記録した時刻
     */
    public record Entry(int rank, String playerName, int score, Instant recordedAt) {

        static Entry of(int rank, GameScore score) {
            return new Entry(rank, score.playerName().value(), score.score(), score.recordedAt());
        }
    }

    public static RankingResponse from(Ranking ranking) {
        List<GameScore> scores = ranking.entries();
        List<Entry> entries = IntStream.range(0, scores.size())
                .mapToObj(i -> Entry.of(i + 1, scores.get(i)))
                .toList();
        return new RankingResponse(ranking.game().slug(), entries);
    }
}

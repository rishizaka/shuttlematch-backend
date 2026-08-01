package com.shuttlematch.domain.model.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RankingTest {

    private static final Instant T0 = Instant.parse("2026-08-01T00:00:00Z");

    private static GameScore score(int value, int secondsLater) {
        return new GameScore(
                MiniGame.COIN, PlayerName.of("P" + value), value, T0.plusSeconds(secondsLater));
    }

    @Test
    @DisplayName("スコア降順に並び、上位5件だけを持つ")
    void sortsAndLimits() {
        Ranking ranking = new Ranking(MiniGame.COIN, List.of(
                score(30, 0), score(80, 1), score(10, 2), score(50, 3), score(70, 4),
                score(20, 5), score(90, 6)));

        assertThat(ranking.entries()).hasSize(Ranking.SIZE);
        assertThat(ranking.entries().stream().map(GameScore::score))
                .containsExactly(90, 80, 70, 50, 30);
    }

    @Test
    @DisplayName("同点は先に記録した方が上位")
    void earlierWinsOnTie() {
        GameScore first = score(50, 0);
        GameScore later = new GameScore(
                MiniGame.COIN, PlayerName.of("あと"), 50, T0.plusSeconds(60));

        Ranking ranking = new Ranking(MiniGame.COIN, List.of(later, first));

        assertThat(ranking.entries()).containsExactly(first, later);
    }

    @Test
    @DisplayName("空のランキングにはどんなスコアでも1位で入る")
    void emptyAcceptsAnything() {
        Ranking ranking = new Ranking(MiniGame.COIN, List.of());

        assertThat(ranking.rankFor(0)).contains(1);
        assertThat(ranking.accepts(0)).isTrue();
    }

    @Test
    @DisplayName("5件未満なら必ずランクインする")
    void notFullAlwaysAccepts() {
        Ranking ranking = new Ranking(MiniGame.COIN, List.of(score(90, 0), score(80, 1)));

        assertThat(ranking.rankFor(10)).contains(3);
    }

    @Test
    @DisplayName("満席なら5位のスコアを上回ったときだけランクインする")
    void fullNeedsToBeatLast() {
        Ranking ranking = new Ranking(MiniGame.COIN, List.of(
                score(90, 0), score(80, 1), score(70, 2), score(60, 3), score(50, 4)));

        assertThat(ranking.rankFor(51)).contains(5);
        assertThat(ranking.rankFor(85)).contains(2);
        // 5位と同点では入れない(先に記録した方が上位のため)
        assertThat(ranking.rankFor(50)).isEmpty();
        assertThat(ranking.accepts(49)).isFalse();
    }

    @Test
    @DisplayName("ゲームごとの上限を超えるスコアは記録できない")
    void rejectsAbsurdScore() {
        assertThatThrownBy(() -> new GameScore(
                MiniGame.COIN, PlayerName.of("ずる"), Integer.MAX_VALUE, T0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("上限");
    }
}

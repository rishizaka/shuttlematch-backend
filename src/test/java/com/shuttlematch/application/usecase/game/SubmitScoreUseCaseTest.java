package com.shuttlematch.application.usecase.game;

import static org.assertj.core.api.Assertions.assertThat;

import com.shuttlematch.domain.model.game.GameScore;
import com.shuttlematch.domain.model.game.MiniGame;
import com.shuttlematch.domain.model.game.PlayerName;
import com.shuttlematch.domain.model.game.Ranking;
import com.shuttlematch.domain.repository.GameScoreRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SubmitScoreUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-08-01T12:00:00Z");

    /** ランキングの並び替えと切り捨てを実際に行うインメモリ実装。 */
    private static final class InMemoryRepository implements GameScoreRepository {
        private final List<GameScore> saved = new ArrayList<>();

        @Override
        public List<GameScore> findTop(MiniGame game, int limit) {
            return saved.stream()
                    .filter(score -> score.game() == game)
                    .sorted(Comparator.comparingInt(GameScore::score).reversed()
                            .thenComparing(GameScore::recordedAt))
                    .limit(limit)
                    .toList();
        }

        @Override
        public void save(GameScore score) {
            saved.add(score);
        }

        @Override
        public void pruneBeyond(MiniGame game, int keep) {
            List<GameScore> keeping = findTop(game, keep);
            saved.removeIf(score -> score.game() == game && !keeping.contains(score));
        }
    }

    private final InMemoryRepository repository = new InMemoryRepository();
    private final SubmitScoreUseCase useCase =
            new SubmitScoreUseCase(repository, Clock.fixed(NOW, ZoneOffset.UTC));

    private void seed(int... scores) {
        int offset = 0;
        for (int value : scores) {
            repository.save(new GameScore(
                    MiniGame.COIN, PlayerName.of("P" + value), value,
                    NOW.minusSeconds(1000 - offset++)));
        }
    }

    @Test
    @DisplayName("ランクインしたら記録され、順位と更新後のランキングを返す")
    void recordsWhenRankedIn() {
        seed(90, 80, 70, 60, 50);

        SubmitScoreResult result = useCase.execute(MiniGame.COIN, PlayerName.of("りょう"), 75);

        assertThat(result.rankedIn()).isTrue();
        assertThat(result.rank()).contains(3);
        assertThat(result.ranking().entries().stream().map(GameScore::score))
                .containsExactly(90, 80, 75, 70, 60);
    }

    @Test
    @DisplayName("ランクインしないスコアは保存しない")
    void doesNotRecordWhenOutOfRange() {
        seed(90, 80, 70, 60, 50);

        SubmitScoreResult result = useCase.execute(MiniGame.COIN, PlayerName.of("おしい"), 40);

        assertThat(result.rankedIn()).isFalse();
        assertThat(result.rank()).isEmpty();
        assertThat(repository.findTop(MiniGame.COIN, 10)).hasSize(5);
        assertThat(result.ranking().entries().stream().map(GameScore::score))
                .containsExactly(90, 80, 70, 60, 50);
    }

    @Test
    @DisplayName("記録すると6位以下は切り捨てられ、5件を超えて溜まらない")
    void prunesBeyondFive() {
        seed(90, 80, 70, 60, 50);

        useCase.execute(MiniGame.COIN, PlayerName.of("いち"), 100);
        useCase.execute(MiniGame.COIN, PlayerName.of("に"), 95);

        assertThat(repository.findTop(MiniGame.COIN, 100)).hasSize(Ranking.SIZE);
        assertThat(repository.findTop(MiniGame.COIN, 100).stream().map(GameScore::score))
                .containsExactly(100, 95, 90, 80, 70);
    }

    @Test
    @DisplayName("ゲームごとに独立したランキングになる")
    void perGame() {
        seed(90, 80, 70, 60, 50);

        SubmitScoreResult result = useCase.execute(MiniGame.FLAP, PlayerName.of("べつ"), 1);

        assertThat(result.rank()).contains(1);
        assertThat(repository.findTop(MiniGame.COIN, 100)).hasSize(5);
        assertThat(repository.findTop(MiniGame.FLAP, 100)).hasSize(1);
    }
}

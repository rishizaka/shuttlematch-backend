package com.shuttlematch.domain.model.game;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * ミニゲームのランキング(上位 {@link #SIZE} 件)。
 *
 * <p>「このスコアは何位で、そもそもランクインするのか」という判断をここに集める。
 * 昔のゲーセンの表と同じで、<b>同点なら先に記録した方が上位</b>。後から同じ点を出した人は
 * 下に付く(先に出した人の記録を追い落とせない)。
 */
public record Ranking(MiniGame game, List<GameScore> entries) {

    /** ランキングに残す件数。 */
    public static final int SIZE = 5;

    public Ranking {
        Objects.requireNonNull(game, "game は null にできません");
        Objects.requireNonNull(entries, "entries は null にできません");
        entries = entries.stream()
                .sorted(Comparator.comparingInt(GameScore::score).reversed()
                        .thenComparing(GameScore::recordedAt))
                .limit(SIZE)
                .toList();
    }

    /**
     * このスコアを今から記録したときの順位。ランクインしないなら空。
     *
     * <p>同点は既存の記録が上位なので、「自分より大きいか同点の記録の数 + 1」が順位になる。
     */
    public Optional<Integer> rankFor(int score) {
        long better = entries.stream().filter(entry -> entry.score() >= score).count();
        int rank = (int) better + 1;
        return rank <= SIZE ? Optional.of(rank) : Optional.empty();
    }

    /** ランクインするか。 */
    public boolean accepts(int score) {
        return rankFor(score).isPresent();
    }
}

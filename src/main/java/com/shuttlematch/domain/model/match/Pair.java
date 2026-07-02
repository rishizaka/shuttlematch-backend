package com.shuttlematch.domain.model.match;

import com.shuttlematch.domain.model.room.ParticipantId;

import java.util.Objects;

/**
 * ダブルスのペア(値オブジェクト)。異なる2名で構成する。
 * <p>
 * メンバーが同じなら順序に関係なく等価とみなせるよう、生成時に
 * ParticipantId(UUID) の昇順へ正規化する。これにより
 * {@code new Pair(a, b)} と {@code new Pair(b, a)} は equals となる。
 */
public record Pair(ParticipantId player1, ParticipantId player2) {

    public Pair {
        Objects.requireNonNull(player1, "player1 は null にできません");
        Objects.requireNonNull(player2, "player2 は null にできません");
        if (player1.equals(player2)) {
            throw new IllegalArgumentException("ペアは異なる2名で構成する必要があります");
        }
        // 順序を一意化(メンバー集合が同じなら equal になる)
        if (player1.value().compareTo(player2.value()) > 0) {
            ParticipantId tmp = player1;
            player1 = player2;
            player2 = tmp;
        }
    }

    public boolean contains(ParticipantId participant) {
        return player1.equals(participant) || player2.equals(participant);
    }
}

package com.shuttlematch.domain.model.match;

import com.shuttlematch.domain.model.room.ParticipantId;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * 1試合(エンティティ)。ペアA vs ペアB。重複しない4名で構成される。
 * <p>
 * setNumber はセット番号(1始まり)、courtNumber はコート番号(1始まり)。
 * 同じセットの各コートの試合は同時に進行する。
 * startedAt はセット開始時刻(未開始は null。同一セットの試合は同じ時刻になる)。
 */
public record Match(MatchNumber matchNumber, int setNumber, int courtNumber,
                    Pair pairA, Pair pairB, OffsetDateTime startedAt) {

    public Match {
        Objects.requireNonNull(matchNumber, "matchNumber は null にできません");
        Objects.requireNonNull(pairA, "pairA は null にできません");
        Objects.requireNonNull(pairB, "pairB は null にできません");
        if (setNumber < 1) {
            throw new IllegalArgumentException("setNumber は 1 以上である必要があります: " + setNumber);
        }
        if (courtNumber < 1) {
            throw new IllegalArgumentException("courtNumber は 1 以上である必要があります: " + courtNumber);
        }

        Set<ParticipantId> distinct = new HashSet<>();
        distinct.add(pairA.player1());
        distinct.add(pairA.player2());
        distinct.add(pairB.player1());
        distinct.add(pairB.player2());
        if (distinct.size() != 4) {
            throw new IllegalArgumentException("1試合は重複しない4名で構成する必要があります");
        }
    }

    public static Match of(MatchNumber matchNumber, int setNumber, int courtNumber, Pair pairA, Pair pairB) {
        return new Match(matchNumber, setNumber, courtNumber, pairA, pairB, null);
    }

    /** セット開始時刻を記録した新しいインスタンスを返す。 */
    public Match withStartedAt(OffsetDateTime startedAt) {
        return new Match(matchNumber, setNumber, courtNumber, pairA, pairB, startedAt);
    }

    public boolean isStarted() {
        return startedAt != null;
    }

    public boolean hasParticipant(ParticipantId participant) {
        return pairA.contains(participant) || pairB.contains(participant);
    }

    /** 2ペアを順不同の集合として返す。 */
    public Set<Pair> pairs() {
        return Set.of(pairA, pairB);
    }
}

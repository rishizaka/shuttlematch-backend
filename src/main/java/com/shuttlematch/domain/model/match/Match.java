package com.shuttlematch.domain.model.match;

import com.shuttlematch.domain.model.session.ParticipantId;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * 1試合(エンティティ)。ペアA vs ペアB。重複しない4名で構成される。
 * コート番号は任意(未割り当ては null)。
 */
public record Match(MatchNumber matchNumber, Pair pairA, Pair pairB, Integer courtNumber) {

    public Match {
        Objects.requireNonNull(matchNumber, "matchNumber は null にできません");
        Objects.requireNonNull(pairA, "pairA は null にできません");
        Objects.requireNonNull(pairB, "pairB は null にできません");

        Set<ParticipantId> distinct = new HashSet<>();
        distinct.add(pairA.player1());
        distinct.add(pairA.player2());
        distinct.add(pairB.player1());
        distinct.add(pairB.player2());
        if (distinct.size() != 4) {
            throw new IllegalArgumentException("1試合は重複しない4名で構成する必要があります");
        }
    }

    public static Match of(MatchNumber matchNumber, Pair pairA, Pair pairB) {
        return new Match(matchNumber, pairA, pairB, null);
    }

    public Match withCourt(int courtNumber) {
        return new Match(matchNumber, pairA, pairB, courtNumber);
    }

    public boolean hasParticipant(ParticipantId participant) {
        return pairA.contains(participant) || pairB.contains(participant);
    }

    /** 2ペアを順不同の集合として返す(同一カードかどうかの比較に使う)。 */
    public Set<Pair> pairs() {
        return Set.of(pairA, pairB);
    }
}

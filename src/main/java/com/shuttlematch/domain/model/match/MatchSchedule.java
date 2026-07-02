package com.shuttlematch.domain.model.match;

import com.shuttlematch.domain.model.room.ParticipantId;
import com.shuttlematch.domain.model.room.RoomId;

import java.util.List;
import java.util.Objects;

/**
 * 試合スケジュール(集約ルート)。1セッションの全試合を保持する。
 */
public record MatchSchedule(RoomId roomId, List<Match> matches) {

    public MatchSchedule {
        Objects.requireNonNull(roomId, "roomId は null にできません");
        Objects.requireNonNull(matches, "matches は null にできません");
        matches = List.copyOf(matches); // 防御的コピー + 不変化
    }

    public int size() {
        return matches.size();
    }

    /** 指定参加者が出場する試合のみを抽出する。 */
    public List<Match> matchesOf(ParticipantId participant) {
        return matches.stream()
                .filter(match -> match.hasParticipant(participant))
                .toList();
    }

    /**
     * 次に開始できるセット番号(開始済みの最大セット + 1)。
     * セットは 1 から順番にのみ開始できるため、このセット以外は開始できない。
     */
    public int nextStartableSetNumber() {
        int maxStarted = matches.stream()
                .filter(Match::isStarted)
                .mapToInt(Match::setNumber)
                .max()
                .orElse(0);
        return maxStarted + 1;
    }

    /** このスケジュールのセット数。 */
    public int setCount() {
        return matches.stream().mapToInt(Match::setNumber).max().orElse(0);
    }
}

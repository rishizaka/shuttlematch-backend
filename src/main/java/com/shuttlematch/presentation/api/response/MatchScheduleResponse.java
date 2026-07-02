package com.shuttlematch.presentation.api.response;

import com.shuttlematch.domain.model.match.MatchSchedule;

import java.util.List;

/**
 * 試合スケジュールのレスポンス表現。
 */
public record MatchScheduleResponse(String roomId, int matchCount, List<MatchResponse> matches) {

    public static MatchScheduleResponse from(MatchSchedule schedule) {
        List<MatchResponse> matches = schedule.matches().stream()
                .map(MatchResponse::from)
                .toList();
        return new MatchScheduleResponse(
                schedule.roomId().value().toString(),
                matches.size(),
                matches);
    }
}

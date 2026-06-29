package com.shuttlematch.presentation.api.controller;

import com.shuttlematch.application.usecase.session.GenerateMatchesCommand;
import com.shuttlematch.application.usecase.session.GenerateMatchesUseCase;
import com.shuttlematch.application.usecase.session.GetMatchScheduleUseCase;
import com.shuttlematch.domain.model.match.MatchSchedule;
import com.shuttlematch.application.ResourceNotFoundException;
import com.shuttlematch.domain.model.session.SessionId;
import com.shuttlematch.presentation.api.request.GenerateMatchesRequest;
import com.shuttlematch.presentation.api.response.MatchScheduleResponse;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 試合スケジュールの生成・参照を行う REST コントローラ。
 */
@RestController
@RequestMapping("/api/v1/sessions/{sessionId}/matches")
public class MatchController {

    private final GenerateMatchesUseCase generateMatchesUseCase;
    private final GetMatchScheduleUseCase getMatchScheduleUseCase;

    public MatchController(
            GenerateMatchesUseCase generateMatchesUseCase,
            GetMatchScheduleUseCase getMatchScheduleUseCase) {
        this.generateMatchesUseCase = generateMatchesUseCase;
        this.getMatchScheduleUseCase = getMatchScheduleUseCase;
    }

    /** 試合を生成する(既存があれば再生成)。 */
    @PostMapping("/generate")
    public MatchScheduleResponse generate(
            @PathVariable UUID sessionId,
            @Valid @RequestBody(required = false) GenerateMatchesRequest request) {

        GenerateMatchesCommand command = toCommand(SessionId.of(sessionId), request);
        MatchSchedule schedule = generateMatchesUseCase.execute(command);
        return MatchScheduleResponse.from(schedule);
    }

    /** 試合スケジュールを取得する。 */
    @GetMapping
    public MatchScheduleResponse get(@PathVariable UUID sessionId) {
        return getMatchScheduleUseCase.execute(SessionId.of(sessionId))
                .map(MatchScheduleResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "セッション " + sessionId + " の試合スケジュールはまだ生成されていません"));
    }

    private GenerateMatchesCommand toCommand(SessionId sessionId, GenerateMatchesRequest request) {
        if (request == null || request.matchCount() == null) {
            return new GenerateMatchesCommand(sessionId);
        }
        return new GenerateMatchesCommand(sessionId, request.matchCount());
    }
}

package com.shuttlematch.presentation.api.controller;

import com.shuttlematch.application.usecase.session.AddSetsUseCase;
import com.shuttlematch.application.usecase.session.GenerateMatchesCommand;
import com.shuttlematch.application.usecase.session.GenerateMatchesUseCase;
import com.shuttlematch.application.usecase.session.GetMatchScheduleUseCase;
import com.shuttlematch.application.usecase.session.ReplanFutureSetsUseCase;
import com.shuttlematch.application.usecase.session.StartSetUseCase;
import com.shuttlematch.domain.model.match.MatchSchedule;
import com.shuttlematch.application.ResourceNotFoundException;
import com.shuttlematch.domain.model.session.SessionId;
import com.shuttlematch.presentation.api.request.AddSetsRequest;
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
    private final StartSetUseCase startSetUseCase;
    private final AddSetsUseCase addSetsUseCase;
    private final ReplanFutureSetsUseCase replanFutureSetsUseCase;

    public MatchController(
            GenerateMatchesUseCase generateMatchesUseCase,
            GetMatchScheduleUseCase getMatchScheduleUseCase,
            StartSetUseCase startSetUseCase,
            AddSetsUseCase addSetsUseCase,
            ReplanFutureSetsUseCase replanFutureSetsUseCase) {
        this.generateMatchesUseCase = generateMatchesUseCase;
        this.getMatchScheduleUseCase = getMatchScheduleUseCase;
        this.startSetUseCase = startSetUseCase;
        this.addSetsUseCase = addSetsUseCase;
        this.replanFutureSetsUseCase = replanFutureSetsUseCase;
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

    /** 既存スケジュールにセットを追加する(既存の結果は保持)。setCount 省略時は1セット。 */
    @PostMapping("/sets")
    public MatchScheduleResponse addSets(
            @PathVariable UUID sessionId,
            @Valid @RequestBody(required = false) AddSetsRequest request) {
        int additionalSetCount = (request == null || request.setCount() == null) ? 1 : request.setCount();
        return MatchScheduleResponse.from(
                addSetsUseCase.execute(SessionId.of(sessionId), additionalSetCount));
    }

    /** 未開始セットを現在の在席者で再編成する(途中参加・早退の反映)。開始済みセットは保持。 */
    @PostMapping("/replan")
    public MatchScheduleResponse replan(@PathVariable UUID sessionId) {
        return MatchScheduleResponse.from(
                replanFutureSetsUseCase.execute(SessionId.of(sessionId)));
    }

    /** 指定セット(全コート)を開始する(開始時刻を記録)。アクティブなセットは最新開始のものとなる。 */
    @PostMapping("/sets/{setNumber}/start")
    public MatchScheduleResponse startSet(
            @PathVariable UUID sessionId,
            @PathVariable int setNumber) {
        return MatchScheduleResponse.from(
                startSetUseCase.execute(SessionId.of(sessionId), setNumber));
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

package com.shuttlematch.presentation.api.controller;

import com.shuttlematch.application.usecase.room.AddSetsUseCase;
import com.shuttlematch.application.usecase.room.GetMatchScheduleUseCase;
import com.shuttlematch.application.usecase.room.ReplanFutureSetsUseCase;
import com.shuttlematch.application.usecase.room.RevertSetUseCase;
import com.shuttlematch.application.usecase.room.StartSetUseCase;
import com.shuttlematch.application.ResourceNotFoundException;
import com.shuttlematch.domain.model.room.RoomId;
import com.shuttlematch.presentation.api.request.AddSetsRequest;
import com.shuttlematch.presentation.api.response.MatchScheduleResponse;

import com.shuttlematch.presentation.api.RequiresOrganizer;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 試合スケジュールの参照・セット操作を行う REST コントローラ。
 * (試合表はルームのかんたん作成で同時に生成される。単独の生成 API は 2026-11 に廃止)
 */
@RestController
@RequestMapping("/api/v1/rooms/{roomId}/matches")
public class MatchController {

    private final GetMatchScheduleUseCase getMatchScheduleUseCase;
    private final StartSetUseCase startSetUseCase;
    private final AddSetsUseCase addSetsUseCase;
    private final ReplanFutureSetsUseCase replanFutureSetsUseCase;
    private final RevertSetUseCase revertSetUseCase;

    public MatchController(
            GetMatchScheduleUseCase getMatchScheduleUseCase,
            StartSetUseCase startSetUseCase,
            AddSetsUseCase addSetsUseCase,
            ReplanFutureSetsUseCase replanFutureSetsUseCase,
            RevertSetUseCase revertSetUseCase) {
        this.getMatchScheduleUseCase = getMatchScheduleUseCase;
        this.startSetUseCase = startSetUseCase;
        this.addSetsUseCase = addSetsUseCase;
        this.replanFutureSetsUseCase = replanFutureSetsUseCase;
        this.revertSetUseCase = revertSetUseCase;
    }

    /** 既存スケジュールにセットを追加する(既存の結果は保持)。setCount 省略時は1セット。 */
    @RequiresOrganizer
    @PostMapping("/sets")
    public MatchScheduleResponse addSets(
            @PathVariable UUID roomId,
            @Valid @RequestBody(required = false) AddSetsRequest request) {
        int additionalSetCount = (request == null || request.setCount() == null) ? 1 : request.setCount();
        return MatchScheduleResponse.from(
                addSetsUseCase.execute(RoomId.of(roomId), additionalSetCount));
    }

    /** 未開始セットを現在の在席者で再編成する(途中参加・早退の反映)。開始済みセットは保持。 */
    @RequiresOrganizer
    @PostMapping("/replan")
    public MatchScheduleResponse replan(@PathVariable UUID roomId) {
        return MatchScheduleResponse.from(
                replanFutureSetsUseCase.execute(RoomId.of(roomId)));
    }

    /** 指定セット(全コート)を開始する(開始時刻を記録)。アクティブなセットは最新開始のものとなる。 */
    @RequiresOrganizer
    @PostMapping("/sets/{setNumber}/start")
    public MatchScheduleResponse startSet(
            @PathVariable UUID roomId,
            @PathVariable int setNumber) {
        return MatchScheduleResponse.from(
                startSetUseCase.execute(RoomId.of(roomId), setNumber));
    }

    /** 進行中(最後に開始した)セットを開始前に戻す(開始時刻を消す)。 */
    @RequiresOrganizer
    @PostMapping("/sets/{setNumber}/revert")
    public MatchScheduleResponse revertSet(
            @PathVariable UUID roomId,
            @PathVariable int setNumber) {
        return MatchScheduleResponse.from(
                revertSetUseCase.execute(RoomId.of(roomId), setNumber));
    }

    /** 試合スケジュールを取得する。 */
    @GetMapping
    public MatchScheduleResponse get(@PathVariable UUID roomId) {
        return getMatchScheduleUseCase.execute(RoomId.of(roomId))
                .map(MatchScheduleResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "セッション " + roomId + " の試合スケジュールはまだ生成されていません"));
    }
}

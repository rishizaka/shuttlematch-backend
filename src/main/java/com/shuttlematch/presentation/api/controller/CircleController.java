package com.shuttlematch.presentation.api.controller;

import com.shuttlematch.application.usecase.circle.AddMemberCommand;
import com.shuttlematch.application.usecase.circle.AddMemberUseCase;
import com.shuttlematch.application.usecase.circle.CreateCircleCommand;
import com.shuttlematch.application.usecase.circle.CreateCircleUseCase;
import com.shuttlematch.application.usecase.circle.GetCircleUseCase;
import com.shuttlematch.domain.model.circle.Circle;
import com.shuttlematch.domain.model.circle.CircleId;
import com.shuttlematch.domain.model.user.UserId;
import com.shuttlematch.presentation.api.request.AddMemberRequest;
import com.shuttlematch.presentation.api.request.CreateCircleRequest;
import com.shuttlematch.presentation.api.response.CircleResponse;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * サークルの作成・参照・メンバー追加を行う REST コントローラ。
 */
@RestController
@RequestMapping("/api/v1/circles")
public class CircleController {

    private final CreateCircleUseCase createCircleUseCase;
    private final GetCircleUseCase getCircleUseCase;
    private final AddMemberUseCase addMemberUseCase;

    public CircleController(
            CreateCircleUseCase createCircleUseCase,
            GetCircleUseCase getCircleUseCase,
            AddMemberUseCase addMemberUseCase) {
        this.createCircleUseCase = createCircleUseCase;
        this.getCircleUseCase = getCircleUseCase;
        this.addMemberUseCase = addMemberUseCase;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CircleResponse create(@Valid @RequestBody CreateCircleRequest request) {
        CreateCircleCommand command = new CreateCircleCommand(
                request.name(),
                request.description(),
                request.joinPolicy(),
                UserId.of(request.createdBy()));
        return CircleResponse.from(createCircleUseCase.execute(command));
    }

    @GetMapping("/{circleId}")
    public CircleResponse get(@PathVariable UUID circleId) {
        return CircleResponse.from(getCircleUseCase.execute(CircleId.of(circleId)));
    }

    @PostMapping("/{circleId}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public CircleResponse addMember(
            @PathVariable UUID circleId,
            @Valid @RequestBody AddMemberRequest request) {
        AddMemberCommand command = new AddMemberCommand(
                CircleId.of(circleId),
                UserId.of(request.userId()),
                request.roleOrDefault());
        Circle circle = addMemberUseCase.execute(command);
        return CircleResponse.from(circle);
    }
}

package com.shuttlematch.application.usecase.circle;

import com.shuttlematch.domain.model.circle.JoinPolicy;
import com.shuttlematch.domain.model.user.UserId;

/**
 * サークル作成ユースケースの入力。
 */
public record CreateCircleCommand(String name, String description, JoinPolicy joinPolicy, UserId createdBy) {
}

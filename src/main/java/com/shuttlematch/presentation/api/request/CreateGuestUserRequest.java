package com.shuttlematch.presentation.api.request;

import jakarta.validation.constraints.Size;

/**
 * ゲストユーザー作成リクエスト。名前は任意(未指定なら「ゲスト」)。
 */
public record CreateGuestUserRequest(@Size(max = 100) String name) {
}

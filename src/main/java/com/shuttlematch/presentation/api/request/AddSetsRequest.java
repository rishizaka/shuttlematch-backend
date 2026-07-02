package com.shuttlematch.presentation.api.request;

import jakarta.validation.constraints.Positive;

/**
 * セット追加リクエスト。setCount は任意(省略時は1セット追加)。
 */
public record AddSetsRequest(@Positive Integer setCount) {
}

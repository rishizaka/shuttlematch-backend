package com.shuttlematch.presentation.api.request;

import jakarta.validation.constraints.Positive;

/**
 * 試合生成リクエスト。matchCount は任意(省略時はデフォルトの15試合)。
 */
public record GenerateMatchesRequest(@Positive Integer matchCount) {
}

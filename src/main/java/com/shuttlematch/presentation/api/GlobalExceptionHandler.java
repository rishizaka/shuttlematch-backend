package com.shuttlematch.presentation.api;

import com.shuttlematch.application.InvalidCredentialsException;
import com.shuttlematch.application.ResourceNotFoundException;
import com.shuttlematch.application.TooManyRequestsException;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * REST API 共通の例外ハンドラ。RFC 7807 (ProblemDetail) 形式で返す。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** ドメインの不変条件違反(参加者不足・不正な試合数など)→ 400。 */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        return problem(HttpStatus.BAD_REQUEST, "不正なリクエスト", ex.getMessage());
    }

    /** 状態として許可されない操作(参加変更不可・生成不可など)→ 409。 */
    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleIllegalState(IllegalStateException ex) {
        return problem(HttpStatus.CONFLICT, "操作が許可されない状態です", ex.getMessage());
    }

    /** リクエストボディのバリデーション違反 → 400。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .reduce((a, b) -> a + ", " + b)
                .orElse("バリデーションエラー");
        return problem(HttpStatus.BAD_REQUEST, "入力値が不正です", detail);
    }

    /** 外部キー制約違反など(存在しないサークル/ユーザー指定)→ 400。 */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
        return problem(HttpStatus.BAD_REQUEST, "データ整合性エラー",
                "参照先のデータが存在しないか、制約に違反しています");
    }

    /** リソース未存在 → 404。 */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "リソースが見つかりません", ex.getMessage());
    }

    /** 短時間に要求が集中(ミニゲームのスコア連投など)→ 429。 */
    @ExceptionHandler(TooManyRequestsException.class)
    public ProblemDetail handleTooManyRequests(TooManyRequestsException ex) {
        return problem(HttpStatus.TOO_MANY_REQUESTS, "リクエストが多すぎます", ex.getMessage());
    }

    /** 認証失敗 → 401。 */
    @ExceptionHandler(InvalidCredentialsException.class)
    public ProblemDetail handleInvalidCredentials(InvalidCredentialsException ex) {
        return problem(HttpStatus.UNAUTHORIZED, "認証に失敗しました", ex.getMessage());
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}

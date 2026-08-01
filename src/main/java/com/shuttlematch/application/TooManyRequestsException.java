package com.shuttlematch.application;

/**
 * 短時間に同じ送信元から要求が来すぎたことを表す例外(アプリケーション層)。
 * GlobalExceptionHandler が 429 に変換する。
 */
public class TooManyRequestsException extends RuntimeException {

    public TooManyRequestsException(String message) {
        super(message);
    }
}

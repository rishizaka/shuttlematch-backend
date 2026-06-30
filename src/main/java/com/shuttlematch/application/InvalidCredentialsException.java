package com.shuttlematch.application;

/**
 * メールアドレスまたはパスワードが正しくないことを表す例外。
 * GlobalExceptionHandler が 401 に変換する。
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException(String message) {
        super(message);
    }
}

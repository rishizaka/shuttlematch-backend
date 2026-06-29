package com.shuttlematch.presentation.api;

/**
 * リソースが存在しないことを表す例外。GlobalExceptionHandler が 404 に変換する。
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}

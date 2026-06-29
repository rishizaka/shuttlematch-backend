package com.shuttlematch.application;

/**
 * 指定されたリソースが存在しないことを表す例外(アプリケーション層)。
 * GlobalExceptionHandler が 404 に変換する。
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}

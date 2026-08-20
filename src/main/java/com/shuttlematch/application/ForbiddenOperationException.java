package com.shuttlematch.application;

/**
 * その操作を行う権限が無いことを表す例外(アプリケーション層)。
 * GlobalExceptionHandler が 403 に変換する。
 * <p>
 * このアプリにはまだログインを前提とした認可が無い。破壊的な操作は
 * 「共有コード(リンク)を知っていること」を権限の代わりにしている。
 */
public class ForbiddenOperationException extends RuntimeException {

    public ForbiddenOperationException(String message) {
        super(message);
    }
}

package com.shuttlematch.domain.model.circle;

import java.security.SecureRandom;

/**
 * サークルの招待コード(値オブジェクト)。
 */
public record InviteCode(String value) {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int DEFAULT_LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    public InviteCode {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("招待コードは必須です");
        }
        if (value.length() > 32) {
            throw new IllegalArgumentException("招待コードは32文字以内である必要があります");
        }
    }

    /** ランダムな招待コードを生成する。 */
    public static InviteCode generate() {
        StringBuilder sb = new StringBuilder(DEFAULT_LENGTH);
        for (int i = 0; i < DEFAULT_LENGTH; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return new InviteCode(sb.toString());
    }

    public static InviteCode of(String value) {
        return new InviteCode(value);
    }
}

package com.shuttlematch.domain.model.room;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * ルームの運営者トークン。ルームを作成した端末だけが破壊的操作を行えることを示す秘密。
 * <p>
 * 実質パスワードなので平文は発行時にしか存在せず、永続化するのは SHA-256 ハッシュだけ。
 * DB が漏れてもルームを乗っ取れない。ログイン認証と違い総当たりの入口が無く、
 * トークン自体が 256bit の乱数なのでソルトやストレッチングは用いない。
 */
public final class OrganizerToken {

    /** 生成する乱数のバイト長(256bit)。Base64URL にすると 43 文字になる。 */
    private static final int TOKEN_BYTES = 32;

    private static final SecureRandom RANDOM = new SecureRandom();

    private OrganizerToken() {
    }

    /** 新しいトークンの平文を発行する。呼び出し側は発行直後に一度だけクライアントへ返す。 */
    public static String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** 平文トークンのハッシュ(小文字16進64文字)を返す。 */
    public static String hash(String plainToken) {
        if (plainToken == null) {
            throw new IllegalArgumentException("トークンは必須です");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(plainToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 は JLS が全実装に要求しているため到達しない。
            throw new IllegalStateException("SHA-256 が利用できません", e);
        }
    }

    /**
     * 平文トークンが期待するハッシュと一致するか。
     * 一致判定は先頭何文字まで合っていたかを応答時間から推測されないよう定数時間で行う。
     * expectedHash が null(トークン未設定のルーム)の場合は常に false。
     */
    public static boolean matches(String plainToken, String expectedHash) {
        if (plainToken == null || plainToken.isBlank() || expectedHash == null) {
            return false;
        }
        return MessageDigest.isEqual(
                hash(plainToken).getBytes(StandardCharsets.UTF_8),
                expectedHash.getBytes(StandardCharsets.UTF_8));
    }
}

package com.shuttlematch.domain.model.notification;

import java.util.Objects;

/**
 * Expo Push Token(値オブジェクト)。例: {@code ExponentPushToken[xxxxxxxxxxxxxxxxxxxxxx]}。
 *
 * <p>FCM/APNs のトークンではなく Expo が発行する識別子で、送信先は Expo Push API になる。
 * そのため backend は FCM/APNs の資格情報を持たない(EAS 側が保持する)。
 */
public record ExpoPushToken(String value) {

    public ExpoPushToken {
        Objects.requireNonNull(value, "ExpoPushToken は null にできません");
        if (!isValid(value)) {
            throw new IllegalArgumentException("Expo Push Token の形式が不正です: " + value);
        }
    }

    /** Expo が発行する形式(ExponentPushToken[...] / ExpoPushToken[...])か。 */
    public static boolean isValid(String value) {
        return value != null
                && (value.startsWith("ExponentPushToken[") || value.startsWith("ExpoPushToken["))
                && value.endsWith("]");
    }

    public static ExpoPushToken of(String value) {
        return new ExpoPushToken(value);
    }
}

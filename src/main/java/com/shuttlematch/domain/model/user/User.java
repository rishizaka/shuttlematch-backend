package com.shuttlematch.domain.model.user;

import java.util.Objects;

/**
 * ユーザー(集約ルート)。
 * cognitoSub は認証基盤(Cognito)導入後に紐付ける想定で、現時点では任意。
 */
public class User {

    private final UserId id;
    private String cognitoSub;
    private String name;
    private String email;
    /** パスワードの BCrypt ハッシュ。null は未設定(デフォルトパスワード扱い)。 */
    private String passwordHash;

    private User(UserId id, String cognitoSub, String name, String email, String passwordHash) {
        this.id = id;
        this.cognitoSub = cognitoSub;
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
    }

    public static User create(String name, String email, String passwordHash) {
        validateName(name);
        validateEmail(email);
        return new User(UserId.newId(), null, name, email, passwordHash);
    }

    /**
     * ゲストユーザー(メール・パスワードなし)を作成する。
     * 未ログインでルームを作成する人の識別に使う。
     */
    public static User createGuest(String name) {
        validateName(name);
        return new User(UserId.newId(), null, name, null, null);
    }

    public static User reconstitute(
            UserId id, String cognitoSub, String name, String email, String passwordHash) {
        return new User(id, cognitoSub, name, email, passwordHash);
    }

    private static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name は必須です");
        }
    }

    private static void validateEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email は必須です");
        }
    }

    public UserId id() {
        return id;
    }

    public String cognitoSub() {
        return cognitoSub;
    }

    public String name() {
        return name;
    }

    public String email() {
        return email;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public boolean hasPassword() {
        return passwordHash != null;
    }

    /** メール未登録のゲストユーザーか。 */
    public boolean isGuest() {
        return email == null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof User user)) {
            return false;
        }
        return Objects.equals(id, user.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}

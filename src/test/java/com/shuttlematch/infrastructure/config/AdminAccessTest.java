package com.shuttlematch.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AdminAccessTest {

    private AdminAccess with(String configured) {
        return new AdminAccess(new AdminProperties(configured));
    }

    @Test
    @DisplayName("設定したトークンと一致すれば true")
    void matchesConfiguredToken() {
        AdminAccess access = with("s3cret-token");

        assertThat(access.isEnabled()).isTrue();
        assertThat(access.matches("s3cret-token")).isTrue();
    }

    @Test
    @DisplayName("違うトークン・null・空文字は false")
    void rejectsOthers() {
        AdminAccess access = with("s3cret-token");

        assertThat(access.matches("wrong")).isFalse();
        assertThat(access.matches("s3cret-toke")).isFalse();
        assertThat(access.matches("s3cret-tokenX")).isFalse();
        assertThat(access.matches(null)).isFalse();
        assertThat(access.matches("")).isFalse();
    }

    @Test
    @DisplayName("トークン未設定なら、何を出しても通らない(空文字で素通りしない)")
    void disabledWhenNotConfigured() {
        for (String unset : new String[] {null, "", "   "}) {
            AdminAccess access = with(unset);

            assertThat(access.isEnabled()).isFalse();
            assertThat(access.matches("")).isFalse();
            assertThat(access.matches("   ")).isFalse();
            assertThat(access.matches("anything")).isFalse();
        }
    }

    @Test
    @DisplayName("設定値の前後の空白は無視する(環境変数の改行や空白対策)")
    void trimsConfiguredToken() {
        AdminAccess access = with("  padded-token \n");

        assertThat(access.matches("padded-token")).isTrue();
    }
}

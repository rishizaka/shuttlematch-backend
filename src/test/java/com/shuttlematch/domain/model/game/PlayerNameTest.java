package com.shuttlematch.domain.model.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PlayerNameTest {

    @Test
    @DisplayName("前後の空白は落とす")
    void trims() {
        assertThat(PlayerName.of("  りょう  ").value()).isEqualTo("りょう");
    }

    @Test
    @DisplayName("8文字まで入れられる(絵文字はサロゲートペアでも1文字と数える)")
    void allowsUpToEightCodePoints() {
        assertThat(PlayerName.of("12345678").value()).isEqualTo("12345678");
        assertThat(PlayerName.of("🏸🏸🏸🏸🏸🏸🏸🏸").value()).hasSize(16);
    }

    @Test
    @DisplayName("空・9文字以上・制御文字は弾く")
    void rejectsInvalid() {
        assertThatThrownBy(() -> PlayerName.of("   "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PlayerName.of("123456789"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("8 文字");
        assertThatThrownBy(() -> PlayerName.of("あ\nい"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("制御文字");
    }

    @Test
    @DisplayName("スラッグから MiniGame を解決する")
    void resolvesGameBySlug() {
        assertThat(MiniGame.fromSlug("coin")).isEqualTo(MiniGame.COIN);
        assertThat(MiniGame.fromSlug(" FLAP ")).isEqualTo(MiniGame.FLAP);
        assertThatThrownBy(() -> MiniGame.fromSlug("unknown"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

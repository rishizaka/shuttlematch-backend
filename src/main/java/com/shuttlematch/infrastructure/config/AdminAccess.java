package com.shuttlematch.infrastructure.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.stereotype.Component;

/**
 * 管理者トークンの照合。
 *
 * <p>できるのは<b>閲覧だけ</b>。一覧 API に roomId を載せるかどうかの判定にしか使わない。
 * 削除・終了・試合表の作り直しといった破壊的な操作には一切効かないので、万一トークンが
 * 漏れても壊されることはない(見られるのは「roomId を知っている人なら元々見られるもの」と同じ)。
 *
 * <p>トークンは環境変数 {@code APP_ADMIN_TOKEN} から入れる。未設定なら常に false を返し、
 * 管理者アクセスは存在しないものとして振る舞う(設定し忘れた環境で空文字が通らないように、
 * 空のときは比較そのものを行わない)。
 */
@Component
public class AdminAccess {

    private final byte[] expected;

    public AdminAccess(AdminProperties properties) {
        this.expected = properties.token().isEmpty()
                ? null
                : properties.token().getBytes(StandardCharsets.UTF_8);
    }

    /** 管理者トークンが設定されているか(運用の確認用)。 */
    public boolean isEnabled() {
        return expected != null;
    }

    /**
     * リクエストヘッダの値が管理者トークンと一致するか。
     *
     * <p>長さの違いだけでも早期に false を返すが、一致している長さの範囲では
     * {@link MessageDigest#isEqual} を使って内容による時間差を作らない。
     */
    public boolean matches(String presented) {
        if (expected == null || presented == null || presented.isEmpty()) {
            return false;
        }
        return MessageDigest.isEqual(presented.getBytes(StandardCharsets.UTF_8), expected);
    }
}

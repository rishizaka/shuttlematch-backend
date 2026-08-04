package com.shuttlematch.presentation.api.response;

import com.shuttlematch.domain.model.room.RoomId;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * ルームの「公開ID」の算出。roomId の SHA-256 の先頭16桁(hex)。
 * <p>
 * 一覧 API は roomId を返さない(返すと誰でも削除・改変できてしまうため)。かわりにこの
 * 公開IDを返し、クライアントは自分が既に知っている roomId を同じ式でハッシュして突き合わせ、
 * 一致した行だけ手元の roomId でリンクを張る。roomId は UUIDv4(122bit)なので公開IDからの
 * 逆算も総当たりもできない。
 * <p>
 * 変更するとクライアント側の照合が全て外れるため、式は固定とみなすこと
 * (フロントエンドの {@code lib/public-room-id.ts} に同じ実装がある)。
 */
public final class PublicRoomId {

    private static final int LENGTH = 16;

    private PublicRoomId() {
    }

    public static String of(RoomId roomId) {
        return of(roomId.value().toString());
    }

    static String of(String roomId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(roomId.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, LENGTH);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 は Java の標準実装で必ず存在する。
            throw new IllegalStateException("SHA-256 が利用できません", e);
        }
    }
}

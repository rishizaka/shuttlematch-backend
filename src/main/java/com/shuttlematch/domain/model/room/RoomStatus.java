package com.shuttlematch.domain.model.room;

/**
 * セッションのステータス。
 */
public enum RoomStatus {
    /** 準備中。 */
    PREPARING,
    /** 参加受付中。 */
    OPEN,
    /** 試合生成済み。 */
    GENERATED,
    /** 終了。 */
    CLOSED;

    /** 参加者の追加・削除が許可される状態か。 */
    public boolean allowsParticipantChanges() {
        return this == PREPARING || this == OPEN;
    }

    /** 試合生成(再生成含む)が許可される状態か。 */
    public boolean allowsMatchGeneration() {
        return this == OPEN || this == GENERATED;
    }
}

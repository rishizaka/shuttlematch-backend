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

    /**
     * 試合表への変更(セットの追加・開始・巻き戻し・再編成)が許可される状態か。
     * <p>
     * 終了したルームは記録として読むだけにする。一覧では終了したルームの roomId を
     * 公開していて(過去の試合表は誰でも見られる)、認可がまだ無いため、
     * 「読めるが変えられない」をここで担保している。
     */
    public boolean allowsMatchChanges() {
        return this != CLOSED;
    }
}

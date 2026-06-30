package com.shuttlematch.domain.model.session;

/**
 * セッションの公開範囲。
 */
public enum SessionVisibility {
    /** 公開。ログインユーザーなら誰でも参加できる。 */
    PUBLIC,
    /** メンバー限定。サークルメンバーのみ参加でき、非メンバーは参加申請を行う。 */
    MEMBERS_ONLY;

    /** メンバー限定か。 */
    public boolean isMembersOnly() {
        return this == MEMBERS_ONLY;
    }
}

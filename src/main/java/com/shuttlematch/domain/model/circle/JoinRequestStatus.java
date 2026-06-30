package com.shuttlematch.domain.model.circle;

/**
 * 参加申請のステータス。
 */
public enum JoinRequestStatus {
    /** 申請中。 */
    PENDING,
    /** 承認済み。 */
    APPROVED,
    /** 却下。 */
    REJECTED
}

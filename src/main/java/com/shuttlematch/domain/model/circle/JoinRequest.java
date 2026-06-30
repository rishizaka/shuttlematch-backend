package com.shuttlematch.domain.model.circle;

import com.shuttlematch.domain.model.user.UserId;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * サークルへの参加申請(集約ルート)。
 * メンバー限定セッションに非メンバーが参加する際の承認制フローで用いる。
 */
public class JoinRequest {

    private final JoinRequestId id;
    private final CircleId circleId;
    private final UserId userId;
    private JoinRequestStatus status;
    private final OffsetDateTime requestedAt;
    private OffsetDateTime decidedAt;

    private JoinRequest(
            JoinRequestId id, CircleId circleId, UserId userId, JoinRequestStatus status,
            OffsetDateTime requestedAt, OffsetDateTime decidedAt) {
        this.id = id;
        this.circleId = circleId;
        this.userId = userId;
        this.status = status;
        this.requestedAt = requestedAt;
        this.decidedAt = decidedAt;
    }

    /** 新規申請を作成する(申請中で開始)。 */
    public static JoinRequest apply(CircleId circleId, UserId userId) {
        Objects.requireNonNull(circleId, "circleId は必須です");
        Objects.requireNonNull(userId, "userId は必須です");
        return new JoinRequest(JoinRequestId.newId(), circleId, userId,
                JoinRequestStatus.PENDING, OffsetDateTime.now(), null);
    }

    /** 永続化層からの復元用。 */
    public static JoinRequest reconstitute(
            JoinRequestId id, CircleId circleId, UserId userId, JoinRequestStatus status,
            OffsetDateTime requestedAt, OffsetDateTime decidedAt) {
        return new JoinRequest(id, circleId, userId, status, requestedAt, decidedAt);
    }

    /** 承認する。申請中以外からは遷移できない。 */
    public void approve() {
        ensurePending();
        this.status = JoinRequestStatus.APPROVED;
        this.decidedAt = OffsetDateTime.now();
    }

    /** 却下する。申請中以外からは遷移できない。 */
    public void reject() {
        ensurePending();
        this.status = JoinRequestStatus.REJECTED;
        this.decidedAt = OffsetDateTime.now();
    }

    private void ensurePending() {
        if (status != JoinRequestStatus.PENDING) {
            throw new IllegalStateException("この申請は既に処理済みです: " + status);
        }
    }

    public boolean isPending() {
        return status == JoinRequestStatus.PENDING;
    }

    public JoinRequestId id() {
        return id;
    }

    public CircleId circleId() {
        return circleId;
    }

    public UserId userId() {
        return userId;
    }

    public JoinRequestStatus status() {
        return status;
    }

    public OffsetDateTime requestedAt() {
        return requestedAt;
    }

    public OffsetDateTime decidedAt() {
        return decidedAt;
    }
}

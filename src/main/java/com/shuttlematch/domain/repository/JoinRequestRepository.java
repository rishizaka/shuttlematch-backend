package com.shuttlematch.domain.repository;

import com.shuttlematch.domain.model.circle.CircleId;
import com.shuttlematch.domain.model.circle.JoinRequest;
import com.shuttlematch.domain.model.circle.JoinRequestId;
import com.shuttlematch.domain.model.circle.JoinRequestStatus;
import com.shuttlematch.domain.model.user.UserId;

import java.util.List;
import java.util.Optional;

/**
 * 参加申請集約の永続化を担うリポジトリ(ドメイン層のインターフェース)。
 */
public interface JoinRequestRepository {

    JoinRequest save(JoinRequest joinRequest);

    Optional<JoinRequest> findById(JoinRequestId id);

    /** サークルの指定ステータスの申請を申請日時の昇順で取得する。 */
    List<JoinRequest> findByCircleIdAndStatus(CircleId circleId, JoinRequestStatus status);

    /** 同一ユーザーの申請中の申請が存在するか。 */
    boolean existsPending(CircleId circleId, UserId userId);
}

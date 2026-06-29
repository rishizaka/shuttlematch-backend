package com.shuttlematch.domain.repository;

import com.shuttlematch.domain.model.circle.Circle;
import com.shuttlematch.domain.model.circle.CircleId;

import java.util.Optional;

/**
 * サークル集約の永続化を担うリポジトリ(ドメイン層のインターフェース)。
 */
public interface CircleRepository {

    Circle save(Circle circle);

    Optional<Circle> findById(CircleId circleId);
}

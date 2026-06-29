package com.shuttlematch.domain.repository;

import com.shuttlematch.domain.model.user.User;
import com.shuttlematch.domain.model.user.UserId;

import java.util.Optional;

/**
 * ユーザー集約の永続化を担うリポジトリ(ドメイン層のインターフェース)。
 */
public interface UserRepository {

    User save(User user);

    Optional<User> findById(UserId userId);

    boolean existsByEmail(String email);
}

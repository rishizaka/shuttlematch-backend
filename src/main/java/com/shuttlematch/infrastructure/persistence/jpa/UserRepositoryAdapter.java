package com.shuttlematch.infrastructure.persistence.jpa;

import com.shuttlematch.domain.model.user.User;
import com.shuttlematch.domain.model.user.UserId;
import com.shuttlematch.domain.repository.UserRepository;

import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * {@link UserRepository} の JPA 実装。
 */
@Repository
public class UserRepositoryAdapter implements UserRepository {

    private final UserJpaRepository jpaRepository;

    public UserRepositoryAdapter(UserJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public User save(User user) {
        UserEntity entity = jpaRepository.findById(user.id().value())
                .orElseGet(UserEntity::new);
        entity.setId(user.id().value());
        entity.setCognitoSub(user.cognitoSub());
        entity.setName(user.name());
        entity.setEmail(user.email());
        entity.setPasswordHash(user.passwordHash());
        UserEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<User> findById(UserId userId) {
        return jpaRepository.findById(userId.value()).map(this::toDomain);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return jpaRepository.findByEmail(email).map(this::toDomain);
    }

    @Override
    public boolean existsByEmail(String email) {
        return jpaRepository.existsByEmail(email);
    }

    private User toDomain(UserEntity entity) {
        return User.reconstitute(
                UserId.of(entity.getId()),
                entity.getCognitoSub(),
                entity.getName(),
                entity.getEmail(),
                entity.getPasswordHash());
    }
}

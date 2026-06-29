package com.shuttlematch.application.usecase.user;

import com.shuttlematch.application.ResourceNotFoundException;
import com.shuttlematch.domain.model.user.User;
import com.shuttlematch.domain.model.user.UserId;
import com.shuttlematch.domain.repository.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ユーザーを参照するユースケース。
 */
@Service
public class GetUserUseCase {

    private final UserRepository userRepository;

    public GetUserUseCase(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public User execute(UserId userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "ユーザーが見つかりません: " + userId.value()));
    }
}

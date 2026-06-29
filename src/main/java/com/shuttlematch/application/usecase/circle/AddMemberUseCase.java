package com.shuttlematch.application.usecase.circle;

import com.shuttlematch.application.ResourceNotFoundException;
import com.shuttlematch.domain.model.circle.Circle;
import com.shuttlematch.domain.repository.CircleRepository;
import com.shuttlematch.domain.repository.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * サークルにメンバーを追加するユースケース。
 */
@Service
public class AddMemberUseCase {

    private final CircleRepository circleRepository;
    private final UserRepository userRepository;

    public AddMemberUseCase(CircleRepository circleRepository, UserRepository userRepository) {
        this.circleRepository = circleRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public Circle execute(AddMemberCommand command) {
        Circle circle = circleRepository.findById(command.circleId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "サークルが見つかりません: " + command.circleId().value()));
        if (userRepository.findById(command.userId()).isEmpty()) {
            throw new ResourceNotFoundException(
                    "ユーザーが見つかりません: " + command.userId().value());
        }
        circle.addMember(command.userId(), command.role());
        return circleRepository.save(circle);
    }
}

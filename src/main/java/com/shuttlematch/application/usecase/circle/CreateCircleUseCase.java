package com.shuttlematch.application.usecase.circle;

import com.shuttlematch.application.ResourceNotFoundException;
import com.shuttlematch.domain.model.circle.Circle;
import com.shuttlematch.domain.repository.CircleRepository;
import com.shuttlematch.domain.repository.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * サークルを作成するユースケース。作成者は ORGANIZER として自動的にメンバーになる。
 */
@Service
public class CreateCircleUseCase {

    private final CircleRepository circleRepository;
    private final UserRepository userRepository;

    public CreateCircleUseCase(CircleRepository circleRepository, UserRepository userRepository) {
        this.circleRepository = circleRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public Circle execute(CreateCircleCommand command) {
        if (userRepository.findById(command.createdBy()).isEmpty()) {
            throw new ResourceNotFoundException(
                    "作成者ユーザーが見つかりません: " + command.createdBy().value());
        }
        Circle circle = Circle.create(
                command.name(), command.description(), command.joinPolicy(), command.createdBy());
        return circleRepository.save(circle);
    }
}

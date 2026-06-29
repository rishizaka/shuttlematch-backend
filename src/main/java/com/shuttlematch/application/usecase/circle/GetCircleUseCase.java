package com.shuttlematch.application.usecase.circle;

import com.shuttlematch.application.ResourceNotFoundException;
import com.shuttlematch.domain.model.circle.Circle;
import com.shuttlematch.domain.model.circle.CircleId;
import com.shuttlematch.domain.repository.CircleRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * サークルを参照するユースケース。
 */
@Service
public class GetCircleUseCase {

    private final CircleRepository circleRepository;

    public GetCircleUseCase(CircleRepository circleRepository) {
        this.circleRepository = circleRepository;
    }

    @Transactional(readOnly = true)
    public Circle execute(CircleId circleId) {
        return circleRepository.findById(circleId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "サークルが見つかりません: " + circleId.value()));
    }
}

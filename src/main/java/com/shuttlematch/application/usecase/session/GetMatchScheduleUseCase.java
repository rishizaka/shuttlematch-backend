package com.shuttlematch.application.usecase.session;

import com.shuttlematch.domain.model.match.MatchSchedule;
import com.shuttlematch.domain.model.session.SessionId;
import com.shuttlematch.domain.repository.MatchScheduleRepository;

import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * セッションの試合スケジュールを参照するユースケース(クエリ)。
 */
@Service
public class GetMatchScheduleUseCase {

    private final MatchScheduleRepository matchScheduleRepository;

    public GetMatchScheduleUseCase(MatchScheduleRepository matchScheduleRepository) {
        this.matchScheduleRepository = matchScheduleRepository;
    }

    @Transactional(readOnly = true)
    public Optional<MatchSchedule> execute(SessionId sessionId) {
        return matchScheduleRepository.findBySessionId(sessionId);
    }
}

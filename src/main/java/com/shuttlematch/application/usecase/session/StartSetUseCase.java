package com.shuttlematch.application.usecase.session;

import com.shuttlematch.application.ResourceNotFoundException;
import com.shuttlematch.domain.model.match.MatchSchedule;
import com.shuttlematch.domain.model.session.SessionId;
import com.shuttlematch.domain.repository.MatchScheduleRepository;

import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 指定セット(全コートの試合)を開始する(開始時刻を記録する)ユースケース。
 * 「アクティブ(進行中)」なセットは最も新しい開始時刻を持つセットとして導出されるため、
 * 新しいセットを開始すると直前のセットは自動的に非アクティブになる。
 */
@Service
public class StartSetUseCase {

    private final MatchScheduleRepository matchScheduleRepository;
    private final Clock clock;

    public StartSetUseCase(MatchScheduleRepository matchScheduleRepository, Clock clock) {
        this.matchScheduleRepository = matchScheduleRepository;
        this.clock = clock;
    }

    @Transactional
    public MatchSchedule execute(SessionId sessionId, int setNumber) {
        MatchSchedule schedule = matchScheduleRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "試合スケジュールが見つかりません: session=" + sessionId.value()));

        // セットは 1 から順番にのみ開始できる
        int next = schedule.nextStartableSetNumber();
        if (setNumber != next) {
            throw new IllegalStateException(
                    "セットは順番に開始してください。次に開始できるのは第 " + next + " セットです");
        }

        return matchScheduleRepository
                .startSet(sessionId, setNumber, OffsetDateTime.now(clock))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "セットが見つかりません: session=" + sessionId.value() + ", set=" + setNumber));
    }
}

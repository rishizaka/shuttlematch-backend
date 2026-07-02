package com.shuttlematch.application.usecase.session;

import com.shuttlematch.application.ResourceNotFoundException;
import com.shuttlematch.domain.model.match.MatchSchedule;
import com.shuttlematch.domain.model.session.Session;
import com.shuttlematch.domain.model.session.SessionId;
import com.shuttlematch.domain.repository.MatchScheduleRepository;
import com.shuttlematch.domain.repository.SessionRepository;
import com.shuttlematch.domain.service.MatchingDomainService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 参加者の増減(途中参加・早退)を受けて、未開始セットを現在の在席者で再編成するユースケース。
 * 開始済みセットは履歴として保持され、未開始セットのみが作り直される。
 */
@Service
public class ReplanFutureSetsUseCase {

    private final SessionRepository sessionRepository;
    private final MatchScheduleRepository matchScheduleRepository;
    private final MatchingDomainService matchingDomainService;

    public ReplanFutureSetsUseCase(
            SessionRepository sessionRepository,
            MatchScheduleRepository matchScheduleRepository,
            MatchingDomainService matchingDomainService) {
        this.sessionRepository = sessionRepository;
        this.matchScheduleRepository = matchScheduleRepository;
        this.matchingDomainService = matchingDomainService;
    }

    @Transactional
    public MatchSchedule execute(SessionId sessionId) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "セッションが見つかりません: " + sessionId.value()));

        MatchSchedule existing = matchScheduleRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "試合スケジュールがまだ生成されていません: session=" + sessionId.value()));

        int courtCount = session.courtCount() != null ? session.courtCount() : 1;
        MatchSchedule updated = matchingDomainService.replanFuture(
                existing, session.activeParticipantIds(), courtCount);

        matchScheduleRepository.deleteBySessionId(sessionId);
        return matchScheduleRepository.save(updated);
    }
}

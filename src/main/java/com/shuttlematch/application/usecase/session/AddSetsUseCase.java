package com.shuttlematch.application.usecase.session;

import com.shuttlematch.application.ResourceNotFoundException;
import com.shuttlematch.domain.model.match.MatchSchedule;
import com.shuttlematch.domain.model.session.Session;
import com.shuttlematch.domain.repository.MatchScheduleRepository;
import com.shuttlematch.domain.repository.SessionRepository;
import com.shuttlematch.domain.model.session.SessionId;
import com.shuttlematch.domain.service.MatchingDomainService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 既存の試合スケジュールにセットを追加するユースケース。
 * 最初に生成したセット数で足りなかった場合に、これまでの結果を保ったまま継ぎ足す。
 */
@Service
public class AddSetsUseCase {

    private final SessionRepository sessionRepository;
    private final MatchScheduleRepository matchScheduleRepository;
    private final MatchingDomainService matchingDomainService;

    public AddSetsUseCase(
            SessionRepository sessionRepository,
            MatchScheduleRepository matchScheduleRepository,
            MatchingDomainService matchingDomainService) {
        this.sessionRepository = sessionRepository;
        this.matchScheduleRepository = matchScheduleRepository;
        this.matchingDomainService = matchingDomainService;
    }

    @Transactional
    public MatchSchedule execute(SessionId sessionId, int additionalSetCount) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "セッションが見つかりません: " + sessionId.value()));

        MatchSchedule existing = matchScheduleRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "試合スケジュールがまだ生成されていません: session=" + sessionId.value()));

        int courtCount = session.courtCount() != null ? session.courtCount() : 1;
        MatchSchedule updated = matchingDomainService.addSets(
                existing, session.participantIds(), courtCount, additionalSetCount);

        // 既存分の開始時刻もドメインオブジェクトに保持されているため、削除→保存で保たれる。
        matchScheduleRepository.deleteBySessionId(sessionId);
        return matchScheduleRepository.save(updated);
    }
}

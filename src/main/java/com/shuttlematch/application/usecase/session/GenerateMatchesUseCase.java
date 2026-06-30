package com.shuttlematch.application.usecase.session;

import com.shuttlematch.application.ResourceNotFoundException;
import com.shuttlematch.domain.model.match.MatchSchedule;
import com.shuttlematch.domain.model.session.Session;
import com.shuttlematch.domain.repository.MatchScheduleRepository;
import com.shuttlematch.domain.repository.SessionRepository;
import com.shuttlematch.domain.service.MatchingDomainService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * セッションの参加者からダブルスの試合スケジュールを生成し永続化するユースケース。
 * 既にスケジュールがある場合は削除してから生成するため、再生成にも対応する。
 */
@Service
public class GenerateMatchesUseCase {

    private final SessionRepository sessionRepository;
    private final MatchScheduleRepository matchScheduleRepository;
    private final MatchingDomainService matchingDomainService;

    public GenerateMatchesUseCase(
            SessionRepository sessionRepository,
            MatchScheduleRepository matchScheduleRepository,
            MatchingDomainService matchingDomainService) {
        this.sessionRepository = sessionRepository;
        this.matchScheduleRepository = matchScheduleRepository;
        this.matchingDomainService = matchingDomainService;
    }

    @Transactional
    public MatchSchedule execute(GenerateMatchesCommand command) {
        Session session = sessionRepository.findById(command.sessionId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "セッションが見つかりません: " + command.sessionId().value()));

        if (!session.status().allowsMatchGeneration()) {
            throw new IllegalStateException(
                    "このセッションは試合を生成できる状態ではありません: " + session.status());
        }

        int courtCount = session.courtCount() != null ? session.courtCount() : 1;
        MatchSchedule schedule = matchingDomainService.generate(
                session.id(), session.participantIds(), courtCount, command.matchCount());

        // 再生成に対応するため既存スケジュールを削除してから保存する
        matchScheduleRepository.deleteBySessionId(session.id());
        MatchSchedule saved = matchScheduleRepository.save(schedule);

        session.markGenerated();
        sessionRepository.save(session);
        return saved;
    }
}

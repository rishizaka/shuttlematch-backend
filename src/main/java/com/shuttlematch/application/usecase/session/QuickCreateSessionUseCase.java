package com.shuttlematch.application.usecase.session;

import com.shuttlematch.domain.model.match.MatchSchedule;
import com.shuttlematch.domain.model.session.Session;
import com.shuttlematch.domain.repository.MatchScheduleRepository;
import com.shuttlematch.domain.repository.SessionRepository;
import com.shuttlematch.domain.service.MatchingDomainService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * かんたんセッション作成。番号(1..N)の参加者を自動登録し、試合表まで一気に生成する。
 * 名前(ニックネーム)は後から変更できる。
 */
@Service
public class QuickCreateSessionUseCase {

    private final SessionRepository sessionRepository;
    private final MatchScheduleRepository matchScheduleRepository;
    private final MatchingDomainService matchingDomainService;

    public QuickCreateSessionUseCase(
            SessionRepository sessionRepository,
            MatchScheduleRepository matchScheduleRepository,
            MatchingDomainService matchingDomainService) {
        this.sessionRepository = sessionRepository;
        this.matchScheduleRepository = matchScheduleRepository;
        this.matchingDomainService = matchingDomainService;
    }

    @Transactional
    public Session execute(QuickCreateSessionCommand command) {
        if (command.participantCount() < 1) {
            throw new IllegalArgumentException("参加人数は1以上にしてください");
        }

        Session session = Session.create(
                command.title(),
                java.time.OffsetDateTime.now(),
                null, null, command.courtCount(),
                command.createdBy());

        // 番号(1..N)の参加者を登録する。名前は後から変更できる。
        for (int i = 1; i <= command.participantCount(); i++) {
            session.addGuest(String.valueOf(i));
        }

        // 試合表を生成する(コート数・既定のセット数)。人数不足なら例外(400)。
        MatchSchedule schedule = matchingDomainService.generate(
                session.id(), session.activeParticipantIds(),
                command.courtCount(), MatchingDomainService.DEFAULT_SET_COUNT);
        session.markGenerated();

        sessionRepository.save(session);
        matchScheduleRepository.save(schedule);
        return session;
    }
}

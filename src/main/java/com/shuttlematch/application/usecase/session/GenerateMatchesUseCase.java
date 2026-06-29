package com.shuttlematch.application.usecase.session;

import com.shuttlematch.domain.model.match.MatchSchedule;
import com.shuttlematch.domain.model.session.ParticipantId;
import com.shuttlematch.domain.repository.MatchScheduleRepository;
import com.shuttlematch.domain.repository.SessionParticipantRepository;
import com.shuttlematch.domain.service.MatchingDomainService;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * セッションの参加者からダブルスの試合スケジュールを生成し永続化するユースケース。
 * 既にスケジュールがある場合は削除してから生成するため、再生成にも対応する。
 */
@Service
public class GenerateMatchesUseCase {

    private final SessionParticipantRepository participantRepository;
    private final MatchScheduleRepository matchScheduleRepository;
    private final MatchingDomainService matchingDomainService;

    public GenerateMatchesUseCase(
            SessionParticipantRepository participantRepository,
            MatchScheduleRepository matchScheduleRepository,
            MatchingDomainService matchingDomainService) {
        this.participantRepository = participantRepository;
        this.matchScheduleRepository = matchScheduleRepository;
        this.matchingDomainService = matchingDomainService;
    }

    @Transactional
    public MatchSchedule execute(GenerateMatchesCommand command) {
        // TODO: Session 集約の実装後、セッションの存在チェックと
        //       ステータス(OPEN 等)の検証をここに追加する。
        List<ParticipantId> participants =
                participantRepository.findParticipantIds(command.sessionId());

        MatchSchedule schedule = matchingDomainService.generate(
                command.sessionId(), participants, command.matchCount());

        // 再生成に対応するため既存スケジュールを削除してから保存する
        matchScheduleRepository.deleteBySessionId(command.sessionId());
        return matchScheduleRepository.save(schedule);
    }
}

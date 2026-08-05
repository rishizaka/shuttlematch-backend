package com.shuttlematch.application.usecase.room;

import com.shuttlematch.application.ResourceNotFoundException;
import com.shuttlematch.application.notification.RoomNotificationEvents;
import com.shuttlematch.domain.model.match.MatchSchedule;
import com.shuttlematch.domain.model.room.Room;
import com.shuttlematch.domain.model.room.RoomId;
import com.shuttlematch.domain.repository.MatchScheduleRepository;
import com.shuttlematch.domain.repository.RoomRepository;

import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 指定セット(全コートの試合)を開始する(開始時刻を記録する)ユースケース。
 * 「アクティブ(進行中)」なセットは最も新しい開始時刻を持つセットとして導出されるため、
 * 新しいセットを開始すると直前のセットは自動的に非アクティブになる。
 */
@Service
public class StartSetUseCase {

    private final RoomRepository roomRepository;
    private final MatchScheduleRepository matchScheduleRepository;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;

    public StartSetUseCase(
            RoomRepository roomRepository,
            MatchScheduleRepository matchScheduleRepository,
            Clock clock,
            ApplicationEventPublisher eventPublisher) {
        this.roomRepository = roomRepository;
        this.matchScheduleRepository = matchScheduleRepository;
        this.clock = clock;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public MatchSchedule execute(RoomId roomId, int setNumber) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "セッションが見つかりません: " + roomId.value()));
        if (!room.status().allowsMatchChanges()) {
            throw new IllegalStateException("終了したセッションのセットは開始できません");
        }

        MatchSchedule schedule = matchScheduleRepository.findByRoomId(roomId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "試合スケジュールが見つかりません: room=" + roomId.value()));

        // セットは 1 から順番にのみ開始できる
        int next = schedule.nextStartableSetNumber();
        if (setNumber != next) {
            throw new IllegalStateException(
                    "セットは順番に開始してください。次に開始できるのは第 " + next + " セットです");
        }

        MatchSchedule started = matchScheduleRepository
                .startSet(roomId, setNumber, OffsetDateTime.now(clock))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "セットが見つかりません: room=" + roomId.value() + ", set=" + setNumber));

        // 出場者に「あなたの試合です」を届ける。送信はコミット後(リスナー側)。
        eventPublisher.publishEvent(new RoomNotificationEvents.SetStarted(roomId, setNumber));
        return started;
    }
}

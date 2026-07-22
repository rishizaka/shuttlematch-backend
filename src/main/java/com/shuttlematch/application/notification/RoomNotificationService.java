package com.shuttlematch.application.notification;

import com.shuttlematch.domain.model.match.Match;
import com.shuttlematch.domain.model.match.MatchSchedule;
import com.shuttlematch.domain.model.notification.PushSubscription;
import com.shuttlematch.domain.model.room.ParticipantId;
import com.shuttlematch.domain.model.room.Room;
import com.shuttlematch.domain.model.room.RoomId;
import com.shuttlematch.domain.repository.MatchScheduleRepository;
import com.shuttlematch.domain.repository.PushSubscriptionRepository;
import com.shuttlematch.domain.repository.RoomRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * ルームの出来事を購読者へ Push 通知するサービス。
 *
 * <p>「自分の番号」(participantId)を申告している端末には、その人が出場するコートまで含めて
 * 個別化した文面を送る。申告していない端末には汎用の文面を送る。
 *
 * <p>操作した本人を除外しない。運営者はたいていプレーヤーを兼ねるため、
 * 自分の操作でも自分に通知が届くのが自然(1 台での動作確認もこれで成立する)。
 */
@Service
public class RoomNotificationService {

    private static final Logger log = LoggerFactory.getLogger(RoomNotificationService.class);

    private final PushSubscriptionRepository subscriptionRepository;
    private final RoomRepository roomRepository;
    private final MatchScheduleRepository matchScheduleRepository;
    private final PushNotificationSender sender;

    public RoomNotificationService(
            PushSubscriptionRepository subscriptionRepository,
            RoomRepository roomRepository,
            MatchScheduleRepository matchScheduleRepository,
            PushNotificationSender sender) {
        this.subscriptionRepository = subscriptionRepository;
        this.roomRepository = roomRepository;
        this.matchScheduleRepository = matchScheduleRepository;
        this.sender = sender;
    }

    /** セットが開始された。出場者には「あなたの試合です」、それ以外には開始のみ知らせる。 */
    public void notifySetStarted(RoomId roomId, int setNumber) {
        List<PushSubscription> subscriptions = subscriptionRepository.findByRoomId(roomId);
        if (subscriptions.isEmpty()) {
            return;
        }
        Optional<Room> room = roomRepository.findById(roomId);
        String roomTitle = room.map(Room::title).orElse("試合");
        List<Match> setMatches = matchScheduleRepository.findByRoomId(roomId)
                .map(MatchSchedule::matches)
                .orElse(List.of())
                .stream()
                .filter(m -> m.setNumber() == setNumber)
                .toList();

        List<PushMessage> messages = new ArrayList<>();
        for (PushSubscription subscription : subscriptions) {
            Optional<Integer> court = subscription.participantId()
                    .flatMap(participant -> courtOf(setMatches, participant));
            String title;
            String body;
            if (court.isPresent()) {
                title = "あなたの試合です";
                body = "第%dセット・コート%d（%s）".formatted(setNumber, court.get(), roomTitle);
            } else if (subscription.participantId().isPresent()) {
                title = "第%dセットが始まりました".formatted(setNumber);
                body = "あなたは休憩です（%s）".formatted(roomTitle);
            } else {
                title = "第%dセットが始まりました".formatted(setNumber);
                body = roomTitle;
            }
            messages.add(new PushMessage(
                    subscription.token(), title, body, Map.of("roomId", roomId.value().toString())));
        }
        log.info("セット開始を通知します: room={}, set={}, 宛先={}件",
                roomId.value(), setNumber, messages.size());
        sender.send(messages);
    }

    /** ルームが終了した。以後は変化しないので、通知後に購読も掃除する。 */
    public void notifyRoomClosed(RoomId roomId) {
        List<PushSubscription> subscriptions = subscriptionRepository.findByRoomId(roomId);
        if (subscriptions.isEmpty()) {
            return;
        }
        String roomTitle = roomRepository.findById(roomId).map(Room::title).orElse("試合");

        List<PushMessage> messages = subscriptions.stream()
                .map(s -> new PushMessage(
                        s.token(),
                        "お疲れさまでした",
                        "「%s」が終了しました".formatted(roomTitle),
                        Map.of("roomId", roomId.value().toString())))
                .toList();
        log.info("ルーム終了を通知します: room={}, 宛先={}件", roomId.value(), messages.size());
        sender.send(messages);

        // 終了したルームは以後通知しないので購読を残さない。
        subscriptions.forEach(s -> subscriptionRepository.delete(roomId, s.token()));
    }

    /** その参加者がこのセットで出場するコート番号。出場しなければ空。 */
    private Optional<Integer> courtOf(List<Match> setMatches, ParticipantId participant) {
        return setMatches.stream()
                .filter(m -> m.hasParticipant(participant))
                .map(Match::courtNumber)
                .findFirst();
    }
}

package com.shuttlematch.domain.model.session;

import com.shuttlematch.domain.model.circle.CircleId;
import com.shuttlematch.domain.model.user.UserId;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * セッション(集約ルート)。1回の活動日。参加者を保持する。
 */
public class Session {

    /** ダブルスの試合生成に必要な最低人数。定員の下限チェックにも用いる。 */
    private static final int MIN_PARTICIPANTS = 4;

    private final SessionId id;
    private final CircleId circleId;
    private String title;
    private OffsetDateTime heldAt;
    private String location;
    private Integer capacity;
    private SessionStatus status;
    private final UserId createdBy;
    private final List<Participant> participants;

    private Session(
            SessionId id, CircleId circleId, String title, OffsetDateTime heldAt,
            String location, Integer capacity, SessionStatus status, UserId createdBy,
            List<Participant> participants) {
        this.id = id;
        this.circleId = circleId;
        this.title = title;
        this.heldAt = heldAt;
        this.location = location;
        this.capacity = capacity;
        this.status = status;
        this.createdBy = createdBy;
        this.participants = participants;
    }

    /** 新規セッションを作成する(受付中で開始)。 */
    public static Session create(
            CircleId circleId, String title, OffsetDateTime heldAt,
            String location, Integer capacity, UserId createdBy) {
        Objects.requireNonNull(circleId, "circleId は必須です");
        Objects.requireNonNull(createdBy, "createdBy は必須です");
        Objects.requireNonNull(heldAt, "heldAt は必須です");
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title は必須です");
        }
        if (capacity != null && capacity < MIN_PARTICIPANTS) {
            throw new IllegalArgumentException("定員は最低 " + MIN_PARTICIPANTS + " 人以上にしてください");
        }
        return new Session(SessionId.newId(), circleId, title, heldAt, location, capacity,
                SessionStatus.OPEN, createdBy, new ArrayList<>());
    }

    /** 永続化層からの復元用。 */
    public static Session reconstitute(
            SessionId id, CircleId circleId, String title, OffsetDateTime heldAt,
            String location, Integer capacity, SessionStatus status, UserId createdBy,
            List<Participant> participants) {
        return new Session(id, circleId, title, heldAt, location, capacity, status, createdBy,
                new ArrayList<>(participants));
    }

    /** 登録ユーザーを参加させる。重複参加は不可。 */
    public Participant addUser(UserId userId) {
        Objects.requireNonNull(userId, "userId は必須です");
        ensureCanModifyParticipants();
        boolean already = participants.stream().anyMatch(p -> userId.equals(p.userId()));
        if (already) {
            throw new IllegalArgumentException("既にこのセッションに参加しています");
        }
        ensureCapacityAvailable();
        Participant participant = Participant.ofUser(userId);
        participants.add(participant);
        return participant;
    }

    /** ゲストを参加させる。 */
    public Participant addGuest(String guestName) {
        ensureCanModifyParticipants();
        ensureCapacityAvailable();
        Participant participant = Participant.ofGuest(guestName);
        participants.add(participant);
        return participant;
    }

    /** 参加者を削除する。存在しなければ false。 */
    public boolean removeParticipant(ParticipantId participantId) {
        ensureCanModifyParticipants();
        return participants.removeIf(p -> p.id().equals(participantId));
    }

    /** 試合生成済みに遷移する。 */
    public void markGenerated() {
        if (!status.allowsMatchGeneration()) {
            throw new IllegalStateException("このセッションは試合を生成できる状態ではありません: " + status);
        }
        this.status = SessionStatus.GENERATED;
    }

    private void ensureCanModifyParticipants() {
        if (!status.allowsParticipantChanges()) {
            throw new IllegalStateException("このセッションは参加者を変更できる状態ではありません: " + status);
        }
    }

    private void ensureCapacityAvailable() {
        if (capacity != null && participants.size() >= capacity) {
            throw new IllegalArgumentException("定員に達しています");
        }
    }

    public List<ParticipantId> participantIds() {
        return participants.stream().map(Participant::id).toList();
    }

    public List<Participant> participants() {
        return List.copyOf(participants);
    }

    public SessionId id() {
        return id;
    }

    public CircleId circleId() {
        return circleId;
    }

    public String title() {
        return title;
    }

    public OffsetDateTime heldAt() {
        return heldAt;
    }

    public String location() {
        return location;
    }

    public Integer capacity() {
        return capacity;
    }

    public SessionStatus status() {
        return status;
    }

    public UserId createdBy() {
        return createdBy;
    }
}

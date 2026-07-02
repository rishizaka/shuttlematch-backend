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
    private Integer courtCount;
    private SessionStatus status;
    private SessionVisibility visibility;
    private final UserId createdBy;
    private final List<Participant> participants;

    private Session(
            SessionId id, CircleId circleId, String title, OffsetDateTime heldAt,
            String location, Integer capacity, Integer courtCount, SessionStatus status,
            SessionVisibility visibility, UserId createdBy, List<Participant> participants) {
        this.id = id;
        this.circleId = circleId;
        this.title = title;
        this.heldAt = heldAt;
        this.location = location;
        this.capacity = capacity;
        this.courtCount = courtCount;
        this.status = status;
        this.visibility = visibility;
        this.createdBy = createdBy;
        this.participants = participants;
    }

    /** 新規セッションを作成する(受付中・公開で開始)。 */
    public static Session create(
            CircleId circleId, String title, OffsetDateTime heldAt,
            String location, Integer capacity, UserId createdBy) {
        return create(circleId, title, heldAt, location, capacity, null,
                SessionVisibility.PUBLIC, createdBy);
    }

    /** 新規セッションを作成する(受付中で開始)。コート数・公開範囲を指定する。 */
    public static Session create(
            CircleId circleId, String title, OffsetDateTime heldAt,
            String location, Integer capacity, Integer courtCount,
            SessionVisibility visibility, UserId createdBy) {
        Objects.requireNonNull(circleId, "circleId は必須です");
        Objects.requireNonNull(createdBy, "createdBy は必須です");
        Objects.requireNonNull(heldAt, "heldAt は必須です");
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title は必須です");
        }
        if (capacity != null && capacity < MIN_PARTICIPANTS) {
            throw new IllegalArgumentException("定員は最低 " + MIN_PARTICIPANTS + " 人以上にしてください");
        }
        if (courtCount != null && courtCount < 1) {
            throw new IllegalArgumentException("コート数は1以上にしてください");
        }
        SessionVisibility resolved = visibility == null ? SessionVisibility.PUBLIC : visibility;
        return new Session(SessionId.newId(), circleId, title, heldAt, location, capacity, courtCount,
                SessionStatus.OPEN, resolved, createdBy, new ArrayList<>());
    }

    /** 永続化層からの復元用。 */
    public static Session reconstitute(
            SessionId id, CircleId circleId, String title, OffsetDateTime heldAt,
            String location, Integer capacity, Integer courtCount, SessionStatus status,
            SessionVisibility visibility, UserId createdBy, List<Participant> participants) {
        return new Session(id, circleId, title, heldAt, location, capacity, courtCount, status,
                visibility == null ? SessionVisibility.PUBLIC : visibility, createdBy,
                new ArrayList<>(participants));
    }

    /** 登録ユーザーを参加させる。重複参加は不可。生成後(途中参加)も可能。 */
    public Participant addUser(UserId userId) {
        Objects.requireNonNull(userId, "userId は必須です");
        ensureCanAddParticipants();
        boolean already = participants.stream()
                .anyMatch(p -> userId.equals(p.userId()) && p.isActive());
        if (already) {
            throw new IllegalArgumentException("既にこのセッションに参加しています");
        }
        ensureCapacityAvailable();
        Participant participant = Participant.ofUser(userId);
        participants.add(participant);
        return participant;
    }

    /** ゲストを参加させる。生成後(途中参加)も可能。 */
    public Participant addGuest(String guestName) {
        ensureCanAddParticipants();
        ensureCapacityAvailable();
        Participant participant = Participant.ofGuest(guestName);
        participants.add(participant);
        return participant;
    }

    /** 参加者を削除する。存在しなければ false。生成前のみ可(生成後は早退を使う)。 */
    public boolean removeParticipant(ParticipantId participantId) {
        ensureCanModifyParticipants();
        return participants.removeIf(p -> p.id().equals(participantId));
    }

    /** 参加者を早退にする。履歴は残し、未開始セットの編成対象から外す。存在しなければ false。 */
    public boolean markParticipantLeft(ParticipantId participantId) {
        return updateParticipantStatus(participantId, ParticipantStatus.LEFT);
    }

    /** 早退した参加者を在席に戻す。存在しなければ false。 */
    public boolean reactivateParticipant(ParticipantId participantId) {
        return updateParticipantStatus(participantId, ParticipantStatus.ACTIVE);
    }

    private boolean updateParticipantStatus(ParticipantId participantId, ParticipantStatus status) {
        ensureNotClosed();
        for (int i = 0; i < participants.size(); i++) {
            Participant p = participants.get(i);
            if (p.id().equals(participantId)) {
                if (p.status() != status) {
                    participants.set(i, p.withStatus(status));
                }
                return true;
            }
        }
        return false;
    }

    /** 試合生成済みに遷移する。 */
    public void markGenerated() {
        if (!status.allowsMatchGeneration()) {
            throw new IllegalStateException("このセッションは試合を生成できる状態ではありません: " + status);
        }
        this.status = SessionStatus.GENERATED;
    }

    /** セッションを終了する(履歴として残す)。既に終了済みなら例外。 */
    public void close() {
        if (status == SessionStatus.CLOSED) {
            throw new IllegalStateException("このセッションは既に終了しています");
        }
        this.status = SessionStatus.CLOSED;
    }

    private void ensureCanModifyParticipants() {
        if (!status.allowsParticipantChanges()) {
            throw new IllegalStateException("このセッションは参加者を変更できる状態ではありません: " + status);
        }
    }

    /** 参加者の追加は終了済み以外なら可能(生成後の途中参加を許可)。 */
    private void ensureCanAddParticipants() {
        if (status == SessionStatus.CLOSED) {
            throw new IllegalStateException("終了したセッションには参加者を追加できません");
        }
    }

    private void ensureNotClosed() {
        if (status == SessionStatus.CLOSED) {
            throw new IllegalStateException("終了したセッションの参加者は変更できません");
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

    /** 在席中(ACTIVE)の参加者 ID。試合編成の対象。 */
    public List<ParticipantId> activeParticipantIds() {
        return participants.stream().filter(Participant::isActive).map(Participant::id).toList();
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

    public Integer courtCount() {
        return courtCount;
    }

    public SessionStatus status() {
        return status;
    }

    public SessionVisibility visibility() {
        return visibility;
    }

    public UserId createdBy() {
        return createdBy;
    }
}

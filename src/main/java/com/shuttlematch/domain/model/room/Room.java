package com.shuttlematch.domain.model.room;

import com.shuttlematch.domain.model.match.Pair;
import com.shuttlematch.domain.model.user.UserId;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * ルーム(集約ルート)。1回の活動日。参加者を保持する。作成者(createdBy)がオーナー。
 */
public class Room {

    /** ダブルスの試合生成に必要な最低人数。定員の下限チェックにも用いる。 */
    private static final int MIN_PARTICIPANTS = 4;

    /** 1ユーザーが1日に作成できるルーム数の上限(スパム的な連続作成の防止)。 */
    public static final int MAX_ROOMS_PER_USER_PER_DAY = 3;

    private final RoomId id;
    /** URL共有用の短いコード(/r/{code})。 */
    private final String shareCode;
    private String title;
    private OffsetDateTime heldAt;
    private String location;
    private Integer capacity;
    private Integer courtCount;
    private RoomStatus status;
    private final UserId createdBy;
    private final List<Participant> participants;
    /** 常に同じチームで組む固定ペア(大会前などに運営が設定する)。 */
    private final List<Pair> fixedPairs;

    private Room(
            RoomId id, String shareCode, String title, OffsetDateTime heldAt,
            String location, Integer capacity, Integer courtCount, RoomStatus status,
            UserId createdBy, List<Participant> participants, List<Pair> fixedPairs) {
        this.id = id;
        this.shareCode = shareCode;
        this.title = title;
        this.heldAt = heldAt;
        this.location = location;
        this.capacity = capacity;
        this.courtCount = courtCount;
        this.status = status;
        this.createdBy = createdBy;
        this.participants = participants;
        this.fixedPairs = fixedPairs;
    }

    /** 新規作成(受付中で開始)。コート数は任意。 */
    public static Room create(
            String title, OffsetDateTime heldAt,
            String location, Integer capacity, UserId createdBy) {
        return create(title, heldAt, location, capacity, null, createdBy);
    }

    /** 新規作成(受付中で開始)。コート数を指定する。 */
    public static Room create(
            String title, OffsetDateTime heldAt,
            String location, Integer capacity, Integer courtCount, UserId createdBy) {
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
        return new Room(RoomId.newId(), ShareCode.generate(), title, heldAt, location,
                capacity, courtCount, RoomStatus.OPEN, createdBy, new ArrayList<>(),
                new ArrayList<>());
    }

    /** 永続化層からの復元用(固定ペア無し)。 */
    public static Room reconstitute(
            RoomId id, String shareCode, String title, OffsetDateTime heldAt,
            String location, Integer capacity, Integer courtCount, RoomStatus status,
            UserId createdBy, List<Participant> participants) {
        return reconstitute(id, shareCode, title, heldAt, location, capacity, courtCount,
                status, createdBy, participants, List.of());
    }

    /** 永続化層からの復元用(固定ペアを含む)。 */
    public static Room reconstitute(
            RoomId id, String shareCode, String title, OffsetDateTime heldAt,
            String location, Integer capacity, Integer courtCount, RoomStatus status,
            UserId createdBy, List<Participant> participants, List<Pair> fixedPairs) {
        return new Room(id, shareCode, title, heldAt, location, capacity, courtCount, status,
                createdBy, new ArrayList<>(participants), new ArrayList<>(fixedPairs));
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
        boolean removed = participants.removeIf(p -> p.id().equals(participantId));
        if (removed) {
            // 削除された参加者を含む固定ペアも解除する(相方だけ残さない)。
            fixedPairs.removeIf(fp -> fp.contains(participantId));
        }
        return removed;
    }

    /** 参加者を早退にする。履歴は残し、未開始セットの編成対象から外す。存在しなければ false。 */
    public boolean markParticipantLeft(ParticipantId participantId) {
        return updateParticipantStatus(participantId, ParticipantStatus.LEFT);
    }

    /** 早退した参加者を在席に戻す。存在しなければ false。 */
    public boolean reactivateParticipant(ParticipantId participantId) {
        return updateParticipantStatus(participantId, ParticipantStatus.ACTIVE);
    }

    /** ゲスト参加者の名前(ニックネーム)を変更する。存在しなければ false。 */
    public boolean renameParticipant(ParticipantId participantId, String newName) {
        ensureNotClosed();
        for (int i = 0; i < participants.size(); i++) {
            Participant p = participants.get(i);
            if (p.id().equals(participantId)) {
                participants.set(i, p.withGuestName(newName));
                return true;
            }
        }
        return false;
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
        this.status = RoomStatus.GENERATED;
    }

    /** セッションを終了する(履歴として残す)。既に終了済みなら例外。 */
    public void close() {
        if (status == RoomStatus.CLOSED) {
            throw new IllegalStateException("このセッションは既に終了しています");
        }
        this.status = RoomStatus.CLOSED;
    }

    private void ensureCanModifyParticipants() {
        if (!status.allowsParticipantChanges()) {
            throw new IllegalStateException("このセッションは参加者を変更できる状態ではありません: " + status);
        }
    }

    /** 参加者の追加は終了済み以外なら可能(生成後の途中参加を許可)。 */
    private void ensureCanAddParticipants() {
        if (status == RoomStatus.CLOSED) {
            throw new IllegalStateException("終了したセッションには参加者を追加できません");
        }
    }

    private void ensureNotClosed() {
        if (status == RoomStatus.CLOSED) {
            throw new IllegalStateException("終了したセッションの参加者は変更できません");
        }
    }

    private void ensureCapacityAvailable() {
        if (capacity != null && participants.size() >= capacity) {
            throw new IllegalArgumentException("定員に達しています");
        }
    }

    /** 固定ペアを追加する。両者がこのルームの参加者で、どちらも既存の固定ペアに属さないこと。 */
    public Pair addFixedPair(ParticipantId a, ParticipantId b) {
        ensureNotClosed();
        Objects.requireNonNull(a, "a は必須です");
        Objects.requireNonNull(b, "b は必須です");
        if (a.equals(b)) {
            throw new IllegalArgumentException("固定ペアは異なる2名で構成してください");
        }
        if (!isParticipant(a) || !isParticipant(b)) {
            throw new IllegalArgumentException("固定ペアはこのルームの参加者で構成してください");
        }
        for (Pair existing : fixedPairs) {
            if (existing.contains(a) || existing.contains(b)) {
                throw new IllegalArgumentException("既に固定ペアに含まれている参加者がいます");
            }
        }
        Pair pair = new Pair(a, b);
        fixedPairs.add(pair);
        return pair;
    }

    /** 固定ペアを解除する。存在しなければ false。 */
    public boolean removeFixedPair(ParticipantId a, ParticipantId b) {
        ensureNotClosed();
        return fixedPairs.remove(new Pair(a, b));
    }

    public List<Pair> fixedPairs() {
        return List.copyOf(fixedPairs);
    }

    private boolean isParticipant(ParticipantId id) {
        return participants.stream().anyMatch(p -> p.id().equals(id));
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

    public RoomId id() {
        return id;
    }

    public String shareCode() {
        return shareCode;
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

    public RoomStatus status() {
        return status;
    }

    public UserId createdBy() {
        return createdBy;
    }
}

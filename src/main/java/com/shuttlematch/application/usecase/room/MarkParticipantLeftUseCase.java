package com.shuttlematch.application.usecase.room;

import com.shuttlematch.application.ResourceNotFoundException;
import com.shuttlematch.domain.model.room.ParticipantId;
import com.shuttlematch.domain.model.room.Room;
import com.shuttlematch.domain.model.room.RoomId;
import com.shuttlematch.domain.repository.MatchScheduleRepository;
import com.shuttlematch.domain.repository.RoomRepository;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 参加者を早退にする(在席状態を LEFT に更新)。履歴は残り、未開始セットの編成対象から外れる。
 * <p>
 * {@link com.shuttlematch.infrastructure.persistence.jpa.RoomRepositoryAdapter#save} は
 * Room 集約を丸ごと差分保存する(保存時点の DB を読み直し、参加者ごとに値が違う行だけ更新する)。
 * そのため <b>同じルームへ2つの更新が同時に走ると、片方の変更がもう片方の古い在庫値で
 * 上書きされて消える</b>(いわゆる lost update)。複数人をまとめて早退にする場合、
 * 1人ずつ {@link #execute} を並行で呼ぶとこの事故が起きうるため、{@link #executeMany} で
 * 1回の読み込み・1回の保存にまとめて安全にする。
 * <p>
 * <b>試合表が既に生成されているなら、未開始セットの再編成もこの操作の中で自動的に行う。</b>
 * 以前はフロント側で「再編成しますか？」と別途尋ねる作りだったため、これを見送ると
 * 早退にした人が未開始セット(まだ始まっていないセット)に残ったままになり、その後
 * 「セット追加」で新しいセットを継ぎ足しても古い未開始セットはそのままなので、
 * 早退したはずの人が試合表に残り続けているように見える不具合が繰り返し起きていた
 * (2026-09-15, 2026-09-19に本番で観測)。早退という操作自体に再編成まで含めることで、
 * 「再編成を忘れる」という選択肢自体を無くす。
 */
@Service
public class MarkParticipantLeftUseCase {

    private final RoomRepository roomRepository;
    private final MatchScheduleRepository matchScheduleRepository;
    private final ReplanFutureSetsUseCase replanFutureSetsUseCase;

    public MarkParticipantLeftUseCase(
            RoomRepository roomRepository,
            MatchScheduleRepository matchScheduleRepository,
            ReplanFutureSetsUseCase replanFutureSetsUseCase) {
        this.roomRepository = roomRepository;
        this.matchScheduleRepository = matchScheduleRepository;
        this.replanFutureSetsUseCase = replanFutureSetsUseCase;
    }

    @Transactional
    public Room execute(RoomId roomId, ParticipantId participantId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "セッションが見つかりません: " + roomId.value()));

        if (!room.markParticipantLeft(participantId)) {
            throw new ResourceNotFoundException("参加者が見つかりません: " + participantId.value());
        }
        Room saved = roomRepository.save(room);
        replanIfScheduled(roomId);
        return saved;
    }

    /**
     * 複数の参加者をまとめて早退にする。1回の読み込み・保存で済ませるため、
     * execute() を並行で複数回呼ぶより安全(クラス javadoc の lost update を参照)。
     * 途中の参加者が見つからなければ例外を投げて、その時点で保存前なので何も反映されない
     * (全員ぶん反映されるか、1件も反映されないかのどちらかになる)。再編成は全員ぶん
     * 反映したあとに1回だけ行う。
     */
    @Transactional
    public Room executeMany(RoomId roomId, List<ParticipantId> participantIds) {
        if (participantIds == null || participantIds.isEmpty()) {
            throw new IllegalArgumentException("参加者を指定してください");
        }
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "セッションが見つかりません: " + roomId.value()));

        for (ParticipantId participantId : participantIds) {
            if (!room.markParticipantLeft(participantId)) {
                throw new ResourceNotFoundException(
                        "参加者が見つかりません: " + participantId.value());
            }
        }
        Room saved = roomRepository.save(room);
        replanIfScheduled(roomId);
        return saved;
    }

    /**
     * 試合表が既に生成されているルームだけ、未開始セットを在席者だけで組み直す。
     * 生成前(試合表がまだ無い)のルームでは何もしない
     * ({@link ReplanFutureSetsUseCase} は試合表が無いと例外を投げるため)。
     */
    private void replanIfScheduled(RoomId roomId) {
        if (matchScheduleRepository.findByRoomId(roomId).isPresent()) {
            replanFutureSetsUseCase.execute(roomId);
        }
    }
}

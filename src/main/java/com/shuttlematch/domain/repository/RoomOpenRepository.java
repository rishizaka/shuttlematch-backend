package com.shuttlematch.domain.repository;

import com.shuttlematch.domain.model.room.RoomId;

/**
 * 参加者の端末で試合表が開かれた記録(KPI 用の計測値)を書くリポジトリ。
 * <p>
 * ルームの集約({@link com.shuttlematch.domain.model.room.Room})には載せない。
 * 運営の振る舞いに関係しない計測値なので、集約の読み書きとは切り離して1行の更新で済ませる。
 */
public interface RoomOpenRepository {

    /**
     * 開かれた回数を1増やし、初回なら日時を記録する。
     *
     * @return ルームが存在して記録できたら true、存在しなければ false
     */
    boolean recordParticipantOpen(RoomId roomId);
}

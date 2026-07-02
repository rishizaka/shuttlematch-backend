package com.shuttlematch.infrastructure.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * matches テーブルに対応する JPA エンティティ。
 * ペアの各プレイヤーは room_participants(id) を参照する。
 */
@Entity
@Table(name = "matches")
@Getter
@Setter
@NoArgsConstructor
public class MatchEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_schedule_id", nullable = false)
    private MatchScheduleEntity schedule;

    @Column(name = "match_number", nullable = false)
    private int matchNumber;

    @Column(name = "set_number", nullable = false)
    private int setNumber;

    @Column(name = "pair_a_player1_id", nullable = false)
    private UUID pairAPlayer1Id;

    @Column(name = "pair_a_player2_id", nullable = false)
    private UUID pairAPlayer2Id;

    @Column(name = "pair_b_player1_id", nullable = false)
    private UUID pairBPlayer1Id;

    @Column(name = "pair_b_player2_id", nullable = false)
    private UUID pairBPlayer2Id;

    @Column(name = "court_number")
    private Integer courtNumber;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;
}

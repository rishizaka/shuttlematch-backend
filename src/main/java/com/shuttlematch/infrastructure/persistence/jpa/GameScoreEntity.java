package com.shuttlematch.infrastructure.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * game_scores テーブルに対応する JPA エンティティ。ミニゲームのランキング 1 行。
 */
@Entity
@Table(name = "game_scores")
@Getter
@Setter
@NoArgsConstructor
public class GameScoreEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    /** ミニゲームのスラッグ(flap / rain / coin / flick)。 */
    @Column(name = "game", nullable = false)
    private String game;

    @Column(name = "player_name", nullable = false)
    private String playerName;

    @Column(name = "score", nullable = false)
    private int score;

    @Column(name = "recorded_at", nullable = false)
    private OffsetDateTime recordedAt;
}

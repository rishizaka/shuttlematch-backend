package com.shuttlematch.infrastructure.persistence.jpa;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * match_schedules テーブルに対応する JPA エンティティ(集約ルート相当)。
 */
@Entity
@Table(name = "match_schedules")
@Getter
@Setter
@NoArgsConstructor
public class MatchScheduleEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    // DB の default now() に任せる
    @Column(name = "generated_at", insertable = false, updatable = false)
    private OffsetDateTime generatedAt;

    @OneToMany(mappedBy = "schedule", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("matchNumber ASC")
    private List<MatchEntity> matches = new ArrayList<>();

    public void addMatch(MatchEntity match) {
        match.setSchedule(this);
        this.matches.add(match);
    }
}

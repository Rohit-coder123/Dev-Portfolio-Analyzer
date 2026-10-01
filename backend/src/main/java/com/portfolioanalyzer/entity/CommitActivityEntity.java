package com.portfolioanalyzer.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Weekly commit bucket derived from GitHub push events, scoped to a single
 * analysis run ({@link ScoreHistoryEntity}). Feeds the Commit Consistency
 * score calculation and the ng2-charts trend line on the frontend.
 */
@Entity
@Table(name = "commit_activity", uniqueConstraints = {
        @UniqueConstraint(name = "uq_week_per_run", columnNames = {"score_history_id", "week_start_date"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommitActivityEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "score_history_id")
    private ScoreHistoryEntity scoreHistory;

    @Column(name = "week_start_date", nullable = false)
    private LocalDate weekStartDate;

    @Column(name = "commit_count", nullable = false)
    private Integer commitCount;
}

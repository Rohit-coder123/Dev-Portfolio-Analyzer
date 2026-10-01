package com.portfolioanalyzer.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One immutable row per analysis run for a user. This is what allows the
 * frontend to plot a developer's score trend over time, since every run is
 * preserved rather than overwritten.
 */
@Entity
@Table(name = "score_history")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScoreHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(name = "overall_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal overallScore;

    @Column(name = "commit_consistency_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal commitConsistencyScore;

    @Column(name = "repository_quality_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal repositoryQualityScore;

    @Column(name = "code_impact_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal codeImpactScore;

    @Column(name = "tech_diversity_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal techDiversityScore;

    @Column(name = "recent_activity_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal recentActivityScore;

    /** '|'-delimited plain-text insight strings, e.g. "Highly Consistent Contributor|Broad Technology Stack" */
    @Column(columnDefinition = "TEXT")
    private String strengths;

    @Column(columnDefinition = "TEXT")
    private String weaknesses;

    @Column(name = "calculated_at", nullable = false)
    private LocalDateTime calculatedAt;

    @PrePersist
    protected void onCreate() {
        this.calculatedAt = LocalDateTime.now();
    }
}

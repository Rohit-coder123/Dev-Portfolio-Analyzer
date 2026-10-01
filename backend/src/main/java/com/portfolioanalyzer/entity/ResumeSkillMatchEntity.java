package com.portfolioanalyzer.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Result of comparing one predefined skill keyword against a resume PDF and
 * the developer's GitHub repository corpus. Persisted so resume-match
 * results can be reviewed alongside the score history run that produced them.
 */
@Entity
@Table(name = "resume_skill_match")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumeSkillMatchEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "score_history_id", nullable = false)
    private ScoreHistoryEntity scoreHistory;

    @Column(name = "skill_name", nullable = false)
    private String skillName;

    @Column(name = "found_in_resume", nullable = false)
    private Boolean foundInResume;

    @Column(name = "found_in_github", nullable = false)
    private Boolean foundInGithub;

    @Column(name = "github_evidence_strength", nullable = false)
    private Integer githubEvidenceStrength;

    /** One of HIGH / MEDIUM / LOW — enforced at the DB level via CHECK constraint. */
    @Column(name = "confidence_level", nullable = false)
    private String confidenceLevel;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}

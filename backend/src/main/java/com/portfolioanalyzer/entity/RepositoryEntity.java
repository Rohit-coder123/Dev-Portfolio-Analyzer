package com.portfolioanalyzer.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Snapshot of a single GitHub repository belonging to a tracked user, as it
 * looked at the time of the most recent fetch. Historical repo snapshots are
 * intentionally overwritten (see {@code uq_repo_per_user}) — trend data lives
 * in {@link ScoreHistoryEntity} and {@link CommitActivityEntity} instead.
 */
@Entity
@Table(name = "repositories", uniqueConstraints = {
        @UniqueConstraint(name = "uq_repo_per_user", columnNames = {"user_id", "github_repo_id"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepositoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(name = "github_repo_id", nullable = false)
    private Long githubRepoId;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "primary_language")
    private String primaryLanguage;

    @Column(name = "stargazers_count")
    private Integer stargazersCount;

    @Column(name = "forks_count")
    private Integer forksCount;

    @Column(name = "open_issues_count")
    private Integer openIssuesCount;

    @Column(name = "size_kb")
    private Integer sizeKb;

    @Column(name = "is_fork")
    private Boolean isFork;

    @Column(name = "is_archived")
    private Boolean isArchived;

    /** Comma-separated GitHub topics, kept flat for simplicity rather than a join table. */
    @Column(columnDefinition = "TEXT")
    private String topics;

    @Column(name = "repo_created_at")
    private LocalDateTime repoCreatedAt;

    @Column(name = "repo_pushed_at")
    private LocalDateTime repoPushedAt;

    @Column(name = "fetched_at", nullable = false)
    private LocalDateTime fetchedAt;

    @PrePersist
    protected void onCreate() {
        this.fetchedAt = LocalDateTime.now();
    }
}

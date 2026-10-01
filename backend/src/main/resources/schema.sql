-- =============================================================================
-- Developer Portfolio Analyzer - PostgreSQL Schema
-- =============================================================================
-- Run this once against an empty database, e.g.:
--   psql -U postgres -d portfolio_analyzer -f schema.sql
--
-- Design notes:
--  * `users` stores the GitHub profile snapshot for a tracked developer.
--  * `repositories` stores a snapshot of that developer's repos at fetch time.
--  * `score_history` stores one row per analysis run, so score trends over
--    time can be charted (this is what powers "track your score history").
--  * `commit_activity` stores weekly commit buckets derived from GitHub
--    push events; this feeds both the Commit Consistency score and the
--    ng2-charts line chart on the frontend.
--  * `resume_skill_match` stores the per-skill confidence results produced
--    by the Resume Matcher for a given analysis run.
-- =============================================================================

CREATE TABLE IF NOT EXISTS users (
    id                      BIGSERIAL PRIMARY KEY,
    github_id               BIGINT UNIQUE,
    github_username         VARCHAR(255) NOT NULL UNIQUE,
    display_name             VARCHAR(255),
    avatar_url               VARCHAR(1024),
    bio                      TEXT,
    company                  VARCHAR(255),
    location                 VARCHAR(255),
    public_repos_count       INTEGER DEFAULT 0,
    followers_count          INTEGER DEFAULT 0,
    following_count          INTEGER DEFAULT 0,
    github_account_created_at TIMESTAMP,
    last_fetched_at          TIMESTAMP,
    created_at               TIMESTAMP NOT NULL DEFAULT now(),
    updated_at               TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_users_github_username ON users (github_username);


CREATE TABLE IF NOT EXISTS repositories (
    id                    BIGSERIAL PRIMARY KEY,
    user_id               BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    github_repo_id        BIGINT NOT NULL,
    name                  VARCHAR(255) NOT NULL,
    description           TEXT,
    primary_language      VARCHAR(100),
    stargazers_count      INTEGER DEFAULT 0,
    forks_count           INTEGER DEFAULT 0,
    open_issues_count     INTEGER DEFAULT 0,
    size_kb               INTEGER DEFAULT 0,
    is_fork               BOOLEAN DEFAULT FALSE,
    is_archived           BOOLEAN DEFAULT FALSE,
    topics                TEXT,                 -- comma-separated topics/tags, kept flat for simplicity
    repo_created_at       TIMESTAMP,
    repo_pushed_at        TIMESTAMP,
    fetched_at            TIMESTAMP NOT NULL DEFAULT now(),

    CONSTRAINT uq_repo_per_user UNIQUE (user_id, github_repo_id)
);

CREATE INDEX IF NOT EXISTS idx_repositories_user_id ON repositories (user_id);
CREATE INDEX IF NOT EXISTS idx_repositories_language ON repositories (primary_language);


CREATE TABLE IF NOT EXISTS score_history (
    id                          BIGSERIAL PRIMARY KEY,
    user_id                     BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    overall_score               NUMERIC(5,2) NOT NULL,
    commit_consistency_score    NUMERIC(5,2) NOT NULL,
    repository_quality_score    NUMERIC(5,2) NOT NULL,
    code_impact_score           NUMERIC(5,2) NOT NULL,
    tech_diversity_score        NUMERIC(5,2) NOT NULL,
    recent_activity_score       NUMERIC(5,2) NOT NULL,
    strengths                  TEXT,            -- '|'-delimited plain-text insights
    weaknesses                 TEXT,            -- '|'-delimited plain-text insights
    calculated_at               TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_score_history_user_id ON score_history (user_id);
CREATE INDEX IF NOT EXISTS idx_score_history_calculated_at ON score_history (calculated_at);


CREATE TABLE IF NOT EXISTS commit_activity (
    id                BIGSERIAL PRIMARY KEY,
    user_id           BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    score_history_id  BIGINT REFERENCES score_history (id) ON DELETE CASCADE,
    week_start_date   DATE NOT NULL,
    commit_count      INTEGER NOT NULL DEFAULT 0,

    CONSTRAINT uq_week_per_run UNIQUE (score_history_id, week_start_date)
);

CREATE INDEX IF NOT EXISTS idx_commit_activity_user_id ON commit_activity (user_id);
CREATE INDEX IF NOT EXISTS idx_commit_activity_week ON commit_activity (week_start_date);


CREATE TABLE IF NOT EXISTS resume_skill_match (
    id                 BIGSERIAL PRIMARY KEY,
    score_history_id   BIGINT NOT NULL REFERENCES score_history (id) ON DELETE CASCADE,
    skill_name         VARCHAR(100) NOT NULL,
    found_in_resume    BOOLEAN NOT NULL DEFAULT FALSE,
    found_in_github    BOOLEAN NOT NULL DEFAULT FALSE,
    github_evidence_strength INTEGER NOT NULL DEFAULT 0,
    confidence_level   VARCHAR(10) NOT NULL CHECK (confidence_level IN ('HIGH', 'MEDIUM', 'LOW')),
    created_at         TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_resume_skill_match_score_history_id ON resume_skill_match (score_history_id);

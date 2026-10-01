// =============================================================================
// Interfaces mirror the backend DTOs field-for-field. Keep these in sync with
// com.portfolioanalyzer.dto.response.* — Jackson serializes LocalDate as an
// ISO "yyyy-MM-dd" string and BigDecimal as a plain number, both of which map
// cleanly to `string` and `number` here.
// =============================================================================

export interface ScoreBreakdown {
  commitConsistency: number;
  repositoryQuality: number;
  codeImpact: number;
  techDiversity: number;
  recentActivity: number;
}

export interface Insights {
  strengths: string[];
  weaknesses: string[];
}

export interface RepositorySummary {
  name: string;
  description: string | null;
  primaryLanguage: string | null;
  stars: number;
  forks: number;
  sizeKb: number;
  isFork: boolean;
}

export interface CommitTrendPoint {
  weekStartDate: string; // ISO date, e.g. "2026-08-24"
  commitCount: number;
}

export interface DeveloperAnalysisResponse {
  scoreHistoryId: number;
  githubUsername: string;
  displayName: string | null;
  avatarUrl: string | null;
  bio: string | null;

  overallScore: number;
  scoreBreakdown: ScoreBreakdown;
  insights: Insights;

  topRepositories: RepositorySummary[];
  commitTrend: CommitTrendPoint[];

  publicReposCount: number;
  followersCount: number;
}

export type ConfidenceLevel = 'HIGH' | 'MEDIUM' | 'LOW';

export interface SkillMatch {
  skillName: string;
  foundInResume: boolean;
  foundInGithub: boolean;
  githubEvidenceStrength: number;
  confidenceLevel: ConfidenceLevel;
}

export interface ResumeMatchResponse {
  githubUsername: string;
  skillsDetectedInResume: number;
  skillsCorroboratedByGithub: number;
  skillMatches: SkillMatch[];
}

export interface ApiErrorResponse {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
}

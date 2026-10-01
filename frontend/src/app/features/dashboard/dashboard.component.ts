import { CommonModule } from "@angular/common";
import { Component, inject } from "@angular/core";
import { MatCardModule } from "@angular/material/card";
import { MatChipsModule } from "@angular/material/chips";
import { MatDividerModule } from "@angular/material/divider";
import { MatIconModule } from "@angular/material/icon";
import { MatProgressSpinnerModule } from "@angular/material/progress-spinner";
import { MatTooltipModule } from "@angular/material/tooltip";
import { ChartConfiguration, ChartOptions } from "chart.js";
import { NgChartsModule } from "ng2-charts";
import { Observable, map } from "rxjs";

import { GithubAnalyzerService } from "../../core/services/github-analyzer.service";
import {
  CommitTrendPoint,
  DeveloperAnalysisResponse,
} from "../../core/models/github-analysis.interfaces";
import { ProfileSearchFormComponent } from "../profile-search-form/profile-search-form.component";
import { ResumeUploadComponent } from "../resume-upload/resume-upload.component";

interface ScoreBreakdownRow {
  label: string;
  value: number;
  weightLabel: string;
}

@Component({
  selector: "app-dashboard",
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatChipsModule,
    MatDividerModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    NgChartsModule,
    ProfileSearchFormComponent,
    ResumeUploadComponent,
  ],
  templateUrl: "./dashboard.component.html",
  styleUrl: "./dashboard.component.scss",
})
export class DashboardComponent {
  // Declared first and via inject() (rather than as a constructor parameter
  // property) so it's guaranteed to exist before the field initializers
  // below run — class fields with initializers execute in declaration
  // order, and a constructor-parameter property isn't assigned until after
  // those initializers, which caused a "used before initialization" error.
  private readonly githubAnalyzer = inject(GithubAnalyzerService);

  // ---- Observable state, consumed via the async pipe in the template ------
  readonly analysis$: Observable<DeveloperAnalysisResponse | null> =
    this.githubAnalyzer.analysis$;
  readonly analysisLoading$: Observable<boolean> =
    this.githubAnalyzer.analysisLoading$;
  readonly analysisError$: Observable<string | null> =
    this.githubAnalyzer.analysisError$;

  readonly resumeMatch$ = this.githubAnalyzer.resumeMatch$;
  readonly resumeLoading$ = this.githubAnalyzer.resumeLoading$;
  readonly resumeError$ = this.githubAnalyzer.resumeError$;

  readonly scoreBreakdownRows$: Observable<ScoreBreakdownRow[]> =
    this.analysis$.pipe(
      map((analysis) => (analysis ? this.buildBreakdownRows(analysis) : [])),
    );

  readonly chartData$: Observable<ChartConfiguration<"line">["data"]> =
    this.analysis$.pipe(
      map((analysis) => this.buildChartData(analysis?.commitTrend ?? [])),
    );

  readonly chartOptions: ChartOptions<"line"> = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: { display: false },
      tooltip: {
        callbacks: {
          title: (items) => `Week of ${items[0]?.label ?? ""}`,
        },
      },
    },
    scales: {
      x: { grid: { display: false } },
      y: {
        beginAtZero: true,
        ticks: { precision: 0 },
        title: { display: true, text: "Commits" },
      },
    },
  };

  currentUsername: string | null = null;

  onAnalyze(username: string): void {
    this.githubAnalyzer.reset();
    this.currentUsername = username;
    this.githubAnalyzer.analyzeProfile(username).subscribe({
      error: () => {
        /* surfaced via analysisError$ in the template; nothing further to do here */
      },
    });
  }

  onResumeSelected(file: File): void {
    if (!this.currentUsername) {
      return;
    }
    this.githubAnalyzer.matchResume(this.currentUsername, file).subscribe({
      error: () => {
        /* surfaced via resumeError$ */
      },
    });
  }

  confidenceIcon(level: string): string {
    switch (level) {
      case "HIGH":
        return "check_circle";
      case "MEDIUM":
        return "warning";
      default:
        return "cancel";
    }
  }

  private buildBreakdownRows(
    analysis: DeveloperAnalysisResponse,
  ): ScoreBreakdownRow[] {
    const b = analysis.scoreBreakdown;
    return [
      {
        label: "Commit consistency",
        value: b.commitConsistency,
        weightLabel: "30%",
      },
      {
        label: "Repository quality",
        value: b.repositoryQuality,
        weightLabel: "20%",
      },
      { label: "Code impact", value: b.codeImpact, weightLabel: "20%" },
      { label: "Tech diversity", value: b.techDiversity, weightLabel: "15%" },
      { label: "Recent activity", value: b.recentActivity, weightLabel: "15%" },
    ];
  }

  private buildChartData(
    points: CommitTrendPoint[],
  ): ChartConfiguration<"line">["data"] {
    return {
      labels: points.map((p) => this.formatWeekLabel(p.weekStartDate)),
      datasets: [
        {
          label: "Commits per week",
          data: points.map((p) => p.commitCount),
          borderColor: "#5eead4",
          backgroundColor: "rgba(94, 234, 212, 0.15)",
          pointBackgroundColor: "#5eead4",
          tension: 0.3,
          fill: true,
        },
      ],
    };
  }

  private formatWeekLabel(isoDate: string): string {
    const d = new Date(isoDate);
    return d.toLocaleDateString(undefined, { month: "short", day: "numeric" });
  }
}

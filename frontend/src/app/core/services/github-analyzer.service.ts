import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable, catchError, finalize, tap, throwError } from 'rxjs';
import {
  ApiErrorResponse,
  DeveloperAnalysisResponse,
  ResumeMatchResponse,
} from '../models/github-analysis.interfaces';

/**
 * Single source of truth for GitHub-analysis + resume-match state.
 *
 * Rather than have each component manage its own loading/error/data flags,
 * the service exposes them as observables so the dashboard, the search form,
 * and the resume-upload widget all react to the same state stream.
 */
@Injectable({ providedIn: 'root' })
export class GithubAnalyzerService {
  private readonly apiBaseUrl = 'http://localhost:8080/api/v1';

  // ---- Analysis state --------------------------------------------------
  private readonly analysisLoading$$ = new BehaviorSubject<boolean>(false);
  private readonly analysisError$$ = new BehaviorSubject<string | null>(null);
  private readonly analysis$$ = new BehaviorSubject<DeveloperAnalysisResponse | null>(null);

  readonly analysisLoading$: Observable<boolean> = this.analysisLoading$$.asObservable();
  readonly analysisError$: Observable<string | null> = this.analysisError$$.asObservable();
  readonly analysis$: Observable<DeveloperAnalysisResponse | null> = this.analysis$$.asObservable();

  // ---- Resume match state ------------------------------------------------
  private readonly resumeLoading$$ = new BehaviorSubject<boolean>(false);
  private readonly resumeError$$ = new BehaviorSubject<string | null>(null);
  private readonly resumeMatch$$ = new BehaviorSubject<ResumeMatchResponse | null>(null);

  readonly resumeLoading$: Observable<boolean> = this.resumeLoading$$.asObservable();
  readonly resumeError$: Observable<string | null> = this.resumeError$$.asObservable();
  readonly resumeMatch$: Observable<ResumeMatchResponse | null> = this.resumeMatch$$.asObservable();

  constructor(private readonly http: HttpClient) {}

  /**
   * Triggers a full GitHub analysis run for the given username. Updates the
   * shared analysis$/analysisLoading$/analysisError$ streams; components
   * subscribe to those rather than to the return value directly.
   */
  analyzeProfile(username: string): Observable<DeveloperAnalysisResponse> {
    this.analysisLoading$$.next(true);
    this.analysisError$$.next(null);

    const url = `${this.apiBaseUrl}/analysis/${encodeURIComponent(username)}`;

    return this.http.post<DeveloperAnalysisResponse>(url, {}).pipe(
      tap((response) => this.analysis$$.next(response)),
      catchError((err: HttpErrorResponse) => this.handleError(err, this.analysisError$$)),
      finalize(() => this.analysisLoading$$.next(false)),
    );
  }

  /**
   * Uploads a resume PDF to be cross-referenced against the most recently
   * analyzed GitHub profile for `username`.
   */
  matchResume(username: string, file: File): Observable<ResumeMatchResponse> {
    this.resumeLoading$$.next(true);
    this.resumeError$$.next(null);

    const formData = new FormData();
    formData.append('file', file, file.name);

    const url = `${this.apiBaseUrl}/resume/${encodeURIComponent(username)}/match`;

    return this.http.post<ResumeMatchResponse>(url, formData).pipe(
      tap((response) => this.resumeMatch$$.next(response)),
      catchError((err: HttpErrorResponse) => this.handleError(err, this.resumeError$$)),
      finalize(() => this.resumeLoading$$.next(false)),
    );
  }

  /** Clears all analysis + resume state, e.g. before starting a new search. */
  reset(): void {
    this.analysis$$.next(null);
    this.analysisError$$.next(null);
    this.resumeMatch$$.next(null);
    this.resumeError$$.next(null);
  }

  private handleError(
    err: HttpErrorResponse,
    errorSubject: BehaviorSubject<string | null>,
  ): Observable<never> {
    const apiError = err.error as ApiErrorResponse | undefined;
    const message =
      apiError?.message ??
      (err.status === 0
        ? 'Could not reach the backend. Is the Spring Boot server running on port 8080?'
        : `Request failed with status ${err.status}.`);

    errorSubject.next(message);
    return throwError(() => new Error(message));
  }
}

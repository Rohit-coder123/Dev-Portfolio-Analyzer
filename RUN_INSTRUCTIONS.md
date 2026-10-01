# Running the Developer Portfolio Analyzer Locally

This guide starts the PostgreSQL database, the Spring Boot backend, and the
Angular frontend, in that order.

## Prerequisites

| Tool | Version |
|---|---|
| Java (JDK) | 17+ |
| Maven | 3.9+ (or use the included `mvnw` if you add one) |
| Node.js | 18+ |
| npm | 9+ |
| PostgreSQL | 14+ |
| Angular CLI | `npm install -g @angular/cli@17` |

You will also want a GitHub **Personal Access Token** (classic, no scopes
required for public data). Unauthenticated requests are capped at 60/hour;
an authenticated token raises that to 5,000/hour.

---

## 1. Set up PostgreSQL

```bash
# Create the database
createdb portfolio_analyzer

# Apply the schema
psql -U postgres -d portfolio_analyzer -f database/schema.sql
```

If you use different credentials, note them for step 2.

---

## 2. Configure and start the backend

```bash
cd backend

# Required environment variables
export DB_HOST=localhost
export DB_PORT=5432
export DB_NAME=portfolio_analyzer
export DB_USERNAME=postgres
export DB_PASSWORD=postgres
export GITHUB_API_TOKEN=ghp_your_personal_access_token_here

# Build and run
mvn clean install
mvn spring-boot:run
```

The API starts on **http://localhost:8080**. Verify it's up:

```bash
curl -X POST http://localhost:8080/api/v1/analysis/torvalds
```

You should get back a JSON payload with `overallScore`, `scoreBreakdown`,
`insights`, `topRepositories`, and `commitTrend`.

### Common backend issues

- **`GithubRateLimitExceededException` / HTTP 429** — you're hitting
  GitHub's rate limit. Make sure `GITHUB_API_TOKEN` is set; unauthenticated
  requests exhaust their 60/hour quota very quickly.
- **`GithubUserNotFoundException` / HTTP 404** — the username doesn't exist
  on GitHub, or was mistyped.
- **Hibernate `SchemaManagementException` on startup** — the schema wasn't
  applied. Re-run step 1, or temporarily set `spring.jpa.hibernate.ddl-auto`
  to `update` in `application.yml` for local iteration.

---

## 3. Configure and start the frontend

Open a second terminal:

```bash
cd frontend
npm install
ng serve
```

The app starts on **http://localhost:4200** and proxies API calls directly
to `http://localhost:8080` (configured in `github-analyzer.service.ts`).

Open http://localhost:4200 in your browser, enter a GitHub username (e.g.
`torvalds`, `octocat`), and click **Analyze profile**. Once results appear,
you can upload a PDF resume to run the Resume Matcher against the same
profile.

---

## 4. Typical local workflow

1. Start PostgreSQL (if not already running as a service).
2. `cd backend && mvn spring-boot:run`
3. In a new terminal: `cd frontend && ng serve`
4. Visit `http://localhost:4200`.

## 5. Building for production

```bash
# Backend — produces an executable JAR
cd backend
mvn clean package
java -jar target/developer-portfolio-analyzer-1.0.0.jar

# Frontend — produces static files in dist/
cd frontend
ng build --configuration production
```

Serve the built frontend from any static host (Nginx, S3+CloudFront, etc.)
and point it at your deployed backend by updating `apiBaseUrl` in
`github-analyzer.service.ts` before building.

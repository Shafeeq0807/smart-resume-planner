# Smart Resume Planner: backend

Spring Boot 3.5 · Java 21 · Spring AI (AIML API) · MySQL 8 · Flyway · Apache Tika · JWT

## Run

1. MySQL 8 running with database `smart_resume` and the user from `application.yml` (or `docker compose up -d mysql`).
2. Set environment variables (see `.env.example`): at least `JWT_SECRET` (48+ chars) and `AIML_API_KEY`.
3. `mvn spring-boot:run` (or run `SmartResumePlannerApplication` from the IDE).
4. Swagger UI: http://localhost:8080/swagger-ui.html. Use **Authorize** with the access token from `/api/auth/login`.

Other AI providers: `SPRING_PROFILES_ACTIVE=openai|groq|ollama` (default profile is `aiml`).

## API map (all under `/api`, JWT required except auth)

| Area | Endpoints |
|---|---|
| Auth | `POST /auth/register, /auth/login, /auth/refresh, /auth/logout`, `GET /auth/me` |
| Profile | `GET/PATCH /users/me`, `POST /users/me/password` |
| Resumes | `POST /resumes` (multipart PDF/DOCX), `GET /resumes`, `GET/PATCH/DELETE /resumes/{id}`, `GET /resumes/{id}/file` |
| Versions | `GET/POST /resumes/{id}/versions`, `POST .../versions/edit`, `POST .../versions/{vid}/activate`, `GET /resumes/{id}/compare?from=&to=` |
| ATS | `GET/POST /resumes/{id}/versions/{vid}/ats` (`?ai=true` adds an AI summary), `.../ats/history` |
| Job descriptions | `POST/GET/DELETE /job-descriptions` |
| Matching | `POST /matches`, `GET /matches`, `GET /matches/{id}` |
| AI optimizer | `POST /optimizer/bullets/analyze`, `POST /optimizer/bullets/apply`, `POST /optimizer/bullets/{id}/reject`, `POST /optimizer/cover-letters`, `POST /optimizer/interview-questions`, `/documents` CRUD |
| Tracker | `/applications` (board, stats, follow-ups, `from-job/{id}`, `PATCH .../move`) |
| Chat | `/chat/sessions`, `POST .../messages`, `POST .../messages/stream` (SSE) |
| GitHub | `POST /github/analyses` (async), `GET /jobs/{id}`, `GET /github/analyses/{id}`, `PATCH` repos, `POST .../to-resume` |
| Export | `GET /templates`, `POST /exports`, `GET /exports/{id}/download` (PDF) |

Errors are RFC 7807 problem JSON with a stable `code`.

## Design notes

- Layers: controller → service → repository → entity, DTO records at the edge. Every user-owned query is scoped by user id (other users' ids return 404).
- Refresh tokens are opaque, stored as SHA-256 hashes, rotated on each use; reuse of an old token revokes the whole family.
- AI calls and file/HTTP work run outside DB transactions; usage is metered in `ai_usage_logs` and limited per user per hour.
- Uploaded resumes are never edited in place: each change creates a new immutable version, so any version can be restored or compared.
- Schema is owned by Flyway (`V1__init_schema.sql`), Hibernate only validates.

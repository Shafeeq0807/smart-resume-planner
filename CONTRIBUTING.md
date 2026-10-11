# Contributing

Thanks for your interest in improving Smart Resume Planner.

## Development setup

1. Start MySQL 8 (`docker compose up -d` in `smart-resume-planner/` starts it with the default dev credentials).
2. Copy the variables from `smart-resume-planner/.env.example` into your IDE run configuration or shell. At minimum set `JWT_SECRET` and `DB_PASSWORD`.
3. Backend: `cd smart-resume-planner && mvn spring-boot:run`.
4. Frontend: `cd smart-resume-frontend && npm install && npm run dev`.

## Workflow

- Create a branch from `main` with a short, descriptive name, e.g. `feature/bullet-optimizer-undo` or `fix/refresh-token-race`.
- Keep pull requests focused on one change. Describe what changed and why, and how you tested it.
- Make sure the backend tests pass (`mvn test`) and the frontend builds (`npm run build`) before opening a PR.
- Use [Conventional Commits](https://www.conventionalcommits.org/) for commit messages, for example `feat(matching): add skill evidence to match results`.

## Conventions

**Backend**
- Layers are controller → service → repository → entity. DTOs are records at the API edge.
- Every user-owned query must be scoped by user id.
- Schema changes go in a new Flyway migration. Never edit an existing `V*__` file.
- Never commit secrets. Read them from environment variables.

**Frontend**
- TypeScript strict mode. Keep API types in `src/api/types.ts` in line with the backend DTOs.
- Keep styling in `src/styles.css`, following the existing class naming.

## Reporting issues

Open an issue with the steps to reproduce, the expected and actual behaviour, and the relevant logs. Remove any personal data or API keys first.

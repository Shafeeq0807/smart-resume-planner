# Smart Resume Planner: frontend

React 18 + TypeScript + Vite, styled as a public website. Anyone can browse the pages; the sign-in / sign-up pop-up appears only when a visitor does something that needs an account (uploading a resume, saving a job, opening a tool), and the action continues automatically after they sign in. Light and dark mode are built in (toggle in the header and footer; the choice is remembered). It talks to the Spring Boot backend over `/api`.

## Step-by-step: run it on your machine

### 1. Install Node.js (once)
Install Node.js 20 or newer from https://nodejs.org (the LTS version). Check it in a terminal:

```
node -v      # v20 or higher
npm -v
```

### 2. Start the backend first
The frontend needs the backend running on port 8080.

1. Start MySQL.
2. In STS (or a terminal in the backend folder) set `JWT_SECRET` and `AIML_API_KEY`, then run the Spring Boot app (`mvn spring-boot:run`).
3. Open http://localhost:8080/swagger-ui.html. If the page loads, the backend is ready.

### 3. Install the frontend
Unzip this project, open a terminal **inside the `smart-resume-frontend` folder** and run:

```
npm install
```

This downloads the libraries into `node_modules/` (about a minute, once).

### 4. Run it
```
npm run dev
```

Open **http://localhost:5173**. The dev server forwards every `/api/...` call to `http://localhost:8080`, so you do not need to configure CORS.

### 5. Try the full flow (about 5 minutes)
1. Open the home page (the ATS checker). Switch light/dark with the moon/sun button.
2. Drop a PDF or DOCX into the upload box and click **Check my score**. Because you are not signed in, the pop-up opens. Create an account in it: the scan starts straight away and you land on your report.
3. **Content & edit**: change some text and save. This creates version 2 and re-scores it.
4. **Versions & compare**: compare v1 and v2, or make an older version current again.
5. **Export PDF**: pick a template and download a PDF.
6. **Tools → Job Match**: paste a job description (80+ characters), save, then **Run match**.
7. On the job page: **Write cover letter**, **Interview questions**, **Add to tracker**.
8. **Tools → Bullet Optimizer**: find weak bullets, replace any `[X%]` placeholder with a real number, tick the ones you want, and apply them as a new version.
9. **Tools → Application Tracker**: drag the card between columns.
10. **Tools → AI Career Coach**: chat, with the answer streaming in live.
11. **Tools → GitHub Projects**: enter a username, tick projects and add them to a resume.
12. Avatar menu (top right): Dashboard, Profile & password, Sign out.

### Troubleshooting
| What you see | Fix |
|---|---|
| Red "Request failed (500)" or the page cannot reach the API | The backend is not running, or is not on port 8080. Start it, or change the proxy target in `vite.config.ts`. |
| "The AI service is not available" | The `AIML_API_KEY` is missing or wrong, or the model name is not available on your plan. |
| `npm install` fails | Check `node -v` is 20 or higher, then delete the `node_modules` folder and run `npm install` again. |
| Port 5173 is busy | Vite picks the next free port and prints it. Use that address. If you see a CORS error, add that origin to the backend `CORS_ORIGINS` variable. |
| You are signed out unexpectedly | Your refresh token expired (or was used twice). Sign in again. |

## Production build
```
npm run build        # outputs static files to dist/
npm run preview      # serves dist/ locally to check it
```

Serve `dist/` from any static host. Because it is on a different origin from the API, build with the API address and allow that origin in the backend:

```
VITE_API_URL=https://api.example.com npm run build
# backend: CORS_ORIGINS=https://app.example.com
```

(If you serve `dist/` and `/api` from the same domain through a reverse proxy, leave `VITE_API_URL` unset.)

## Project layout
```
src/
  api/client.ts       fetch wrapper: JWT header, automatic refresh on 401, error parsing, file download, SSE chat stream
  api/types.ts        TypeScript types matching the backend DTOs
  auth/AuthContext    current user, sign in/up/out, openAuth() and requireAuth(action) for the pop-up
  auth/AuthModal      the sign-in / sign-up pop-up
  theme/              light/dark theme (saved in the browser, defaults to the system setting)
  components/Layout   website header (Tools menu, avatar menu, mobile menu) and footer
  components/Gate     what signed-out visitors see on a tool page, with a button that opens the pop-up
  components/ui       shared UI (cards, score ring, badges, async hooks)
  pages/HomePage      the public landing page: upload hero, how it works, FAQ
  pages/              one file per screen
  styles.css          all styling
```

## How sign-in works
- Sign-in returns a short-lived access token and a long-lived refresh token; both are kept in `localStorage`.
- Every request sends the access token. On a 401 the client refreshes once (shared across parallel requests) and retries.
- If the refresh fails you are returned to the sign-in page.

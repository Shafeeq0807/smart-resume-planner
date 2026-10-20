import { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../api/client";
import type { GithubAnalysis, GithubRepo, Job, Paged, ResumeSummary, VersionDetail } from "../api/types";
import { useAuth } from "../auth/AuthContext";
import { Badge, Bar, Card, Empty, ErrorBox, PageHeader, ScoreRing, Spinner, fmtDate, useAction, useAsync } from "../components/ui";

interface Start { jobId: string; analysisId: number; status: string; cached: boolean }

export default function GithubPage() {
  const { user } = useAuth();
  const past = useAsync(() => api.get<Paged<GithubAnalysis>>("/api/github/analyses?size=10"), []);
  const resumes = useAsync(() => api.get<Paged<ResumeSummary>>("/api/resumes?size=50"), []);
  const [username, setUsername] = useState(user?.githubUsername ?? "");
  const [analysis, setAnalysis] = useState<GithubAnalysis | null>(null);
  const [progress, setProgress] = useState<number | null>(null);
  const [resumeId, setResumeId] = useState<number | "">("");
  const [result, setResult] = useState<VersionDetail | null>(null);
  const act = useAction();
  const timer = useRef<number | null>(null);

  useEffect(() => () => { if (timer.current) window.clearTimeout(timer.current); }, []);
  useEffect(() => { if (!analysis && past.data?.items[0]) void loadAnalysis(past.data.items[0].id); }, [past.data]); // eslint-disable-line

  const loadAnalysis = async (id: number) => { const a = await act.run(() => api.get<GithubAnalysis>(`/api/github/analyses/${id}`)); if (a) setAnalysis(a); };

  const poll = (jobId: string, analysisId: number) => {
    timer.current = window.setTimeout(async () => {
      try {
        const j = await api.get<Job>(`/api/jobs/${jobId}`);
        setProgress(j.progress);
        if (j.status === "COMPLETED") { setProgress(null); await loadAnalysis(analysisId); past.reload(); }
        else if (j.status === "FAILED") { setProgress(null); act.setError(new Error(j.errorMessage ?? "GitHub analysis failed")); }
        else poll(jobId, analysisId);
      } catch (e) { setProgress(null); act.setError(e); }
    }, 1500);
  };

  const start = async (refresh: boolean) => {
    setResult(null);
    const s = await act.run(() => api.post<Start>("/api/github/analyses", { username: username.trim() || undefined, refresh }));
    if (!s) return;
    if (s.cached && !refresh) { await loadAnalysis(s.analysisId); return; }
    setProgress(0); poll(s.jobId, s.analysisId);
  };

  const patchRepo = async (r: GithubRepo, body: { includedInResume: boolean; suggestedBullet?: string }) => {
    if (!analysis) return;
    const updated = await act.run(() => api.patch<GithubRepo>(`/api/github/analyses/${analysis.id}/repositories/${r.id}`, body));
    if (updated) setAnalysis({ ...analysis, repositories: (analysis.repositories ?? []).map((x) => (x.id === r.id ? updated : x)) });
  };

  const toResume = async () => {
    if (!analysis) return;
    const chosen = (analysis.repositories ?? []).filter((r) => r.includedInResume).map((r) => r.id);
    const rid = resumeId === "" ? resumes.data?.items[0]?.id : resumeId;
    if (!rid || chosen.length === 0) return;
    const v = await act.run(() => api.post<VersionDetail>(`/api/github/analyses/${analysis.id}/to-resume`, { resumeId: rid, repositoryIds: chosen, label: "Added GitHub projects" }));
    if (v) setResult(v);
  };

  const repos = analysis?.repositories ?? [];
  const selected = repos.filter((r) => r.includedInResume).length;

  return (
    <>
      <PageHeader title="GitHub analysis" subtitle="Turn your public repositories into skills and resume project bullets." />
      <Card>
        <ErrorBox error={act.error} />
        <div className="row">
          <div style={{ minWidth: 240 }}><label>GitHub username</label><input value={username} onChange={(e) => setUsername(e.target.value)} placeholder={user?.githubUsername ?? "octocat"} /></div>
          <button className="btn" style={{ alignSelf: "end" }} disabled={act.busy || progress !== null} onClick={() => start(false)}>Analyze</button>
          <button className="btn ghost" style={{ alignSelf: "end" }} disabled={act.busy || progress !== null} onClick={() => start(true)}>Refresh</button>
        </div>
        {progress !== null && <div style={{ marginTop: 12 }}><small>Analyzing repositories… {progress}%</small><Bar value={progress} /></div>}
        <div className="hint">Results are cached for 24 hours. Set your username once in Profile to skip typing it.</div>
      </Card>
      {past.loading && !analysis ? <Spinner /> : !analysis ? <Empty>No analysis yet. Enter a username above.</Empty> : (
        <>
          <div className="grid two">
            <Card title={`@${analysis.githubUsername}`}>
              <div className="row" style={{ gap: 20 }}>
                <ScoreRing score={analysis.overallScore} size={100} label="Profile score" />
                <div><small>Analyzed {fmtDate(analysis.analyzedAt)}</small>
                  <div className="row" style={{ gap: 6, marginTop: 6 }}>{(analysis.detectedSkills ?? []).map((s) => <Badge key={s} tone="blue">{s}</Badge>)}</div></div>
              </div>
            </Card>
            <Card title="Add projects to a resume">
              <div className="stack">
                <select value={resumeId === "" ? (resumes.data?.items[0]?.id ?? "") : resumeId} onChange={(e) => setResumeId(Number(e.target.value))}>
                  {resumes.data?.items.map((r) => <option key={r.id} value={r.id}>{r.title}</option>)}</select>
                <button className="btn" onClick={toResume} disabled={act.busy || selected === 0 || !resumes.data?.items.length}>Add {selected} selected project{selected === 1 ? "" : "s"}</button>
                {result && <div className="alert ok">Saved as version {result.versionNumber}. <Link to={`/resumes/${result.resumeId}`}>Open resume</Link></div>}
              </div>
            </Card>
          </div>
          <Card title={`Repositories (${repos.length})`}>
            {repos.length === 0 ? <Empty>No public repositories found.</Empty> : repos.map((r) => <RepoRow key={r.id} repo={r} onChange={patchRepo} />)}
          </Card>
        </>
      )}
    </>
  );
}

function RepoRow({ repo, onChange }: { repo: GithubRepo; onChange: (r: GithubRepo, b: { includedInResume: boolean; suggestedBullet?: string }) => void }) {
  const [bullet, setBullet] = useState(repo.suggestedBullet ?? "");
  return (
    <div className="rewrite">
      <div className="row between">
        <label className="row" style={{ margin: 0 }}>
          <input type="checkbox" style={{ width: "auto" }} checked={repo.includedInResume} onChange={(e) => onChange(repo, { includedInResume: e.target.checked, suggestedBullet: bullet })} />
          {repo.url.startsWith("https://") ? <a href={repo.url} target="_blank" rel="noopener noreferrer">{repo.name}</a> : <span>{repo.name}</span>}</label>
        <span className="row" style={{ gap: 6 }}>
          {repo.primaryLanguage && <Badge tone="blue">{repo.primaryLanguage}</Badge>}<Badge>★ {repo.stars}</Badge>
          {repo.readmeQualityScore != null && <Badge tone={repo.readmeQualityScore >= 60 ? "green" : "amber"}>README {repo.readmeQualityScore}</Badge>}
        </span>
      </div>
      {repo.description && <p className="muted" style={{ margin: "6px 0" }}>{repo.description}</p>}
      <textarea rows={2} value={bullet} onChange={(e) => setBullet(e.target.value)} placeholder="Resume bullet for this project"
        onBlur={() => { if (bullet !== (repo.suggestedBullet ?? "")) onChange(repo, { includedInResume: repo.includedInResume, suggestedBullet: bullet }); }} />
    </div>
  );
}

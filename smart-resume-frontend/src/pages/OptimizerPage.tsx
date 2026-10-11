import { useEffect, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { api } from "../api/client";
import type { AnalyzeBulletsResponse, BulletRewrite, JobDescriptionSummary, Paged, ResumeSummary, VersionDetail } from "../api/types";
import { Badge, Card, Empty, ErrorBox, PageHeader, Spinner, useAction, useAsync } from "../components/ui";

const hasPlaceholder = (t: string) => /\[[^\]]*(X|Y|Z|N|number|metric|%)[^\]]*\]/i.test(t);

export default function OptimizerPage() {
  const [params] = useSearchParams();
  const resumes = useAsync(() => api.get<Paged<ResumeSummary>>("/api/resumes?size=50"), []);
  const jobs = useAsync(() => api.get<Paged<JobDescriptionSummary>>("/api/job-descriptions?size=50"), []);
  const [resumeId, setResumeId] = useState<number | null>(params.get("resumeId") ? Number(params.get("resumeId")) : null);
  const [jobId, setJobId] = useState<number | "">(params.get("jobId") ? Number(params.get("jobId")) : "");
  const [rewrites, setRewrites] = useState<BulletRewrite[]>([]);
  const [info, setInfo] = useState<string | null>(null);
  const [edits, setEdits] = useState<Record<number, string>>({});
  const [picked, setPicked] = useState<Record<number, boolean>>({});
  const [applied, setApplied] = useState<VersionDetail | null>(null);
  const act = useAction();

  useEffect(() => { if (resumeId == null && resumes.data?.items.length) setResumeId(resumes.data.items[0].id); }, [resumes.data, resumeId]);

  const load = (list: BulletRewrite[]) => {
    const suggested = list.filter((r) => r.status === "SUGGESTED");
    setRewrites(suggested);
    setEdits(Object.fromEntries(suggested.map((r) => [r.id, r.rewrittenText])));
    setPicked(Object.fromEntries(suggested.map((r) => [r.id, !r.needsUserMetric])));
  };

  useEffect(() => {
    if (resumeId == null) return;
    api.get<BulletRewrite[]>(`/api/optimizer/bullets?resumeId=${resumeId}`).then(load).catch(() => undefined);
  }, [resumeId]);

  const analyze = async () => {
    if (resumeId == null) return;
    setApplied(null); setInfo(null);
    const r = await act.run(() => api.post<AnalyzeBulletsResponse>("/api/optimizer/bullets/analyze", { resumeId, jobDescriptionId: jobId === "" ? null : jobId, maxBullets: 10 }));
    if (r) { load(r.rewrites); setInfo(r.rewrites.length === 0 ? (r.note ?? "No weak bullets found. Nice work.") : `Scanned ${r.bulletsScanned} bullets and found ${r.weakBullets} that can be stronger.`); }
  };

  const blocked = rewrites.filter((r) => picked[r.id] && hasPlaceholder(edits[r.id] ?? ""));
  const chosen = rewrites.filter((r) => picked[r.id]);

  const apply = async () => {
    if (resumeId == null || chosen.length === 0) return;
    const v = await act.run(() => api.post<VersionDetail>("/api/optimizer/bullets/apply", {
      resumeId, label: "AI bullet improvements", choices: chosen.map((r) => ({ rewriteId: r.id, finalText: edits[r.id] })),
    }));
    if (v) { setApplied(v); setRewrites((rs) => rs.filter((r) => !picked[r.id])); }
  };
  const reject = async (id: number) => { if (await act.run(async () => { await api.post(`/api/optimizer/bullets/${id}/reject`); return true; })) setRewrites((rs) => rs.filter((r) => r.id !== id)); };

  return (
    <>
      <PageHeader title="Bullet optimizer" subtitle="AI rewrites weak bullets into achievement statements. You approve every change, and nothing is invented." />
      <Card>
        <ErrorBox error={act.error} />
        {resumes.loading ? <Spinner /> : (
          <div className="row">
            <div style={{ minWidth: 220 }}><label>Resume</label>
              <select value={resumeId ?? ""} onChange={(e) => { setResumeId(Number(e.target.value)); setRewrites([]); setApplied(null); }}>
                {resumes.data?.items.map((r) => <option key={r.id} value={r.id}>{r.title}</option>)}
              </select></div>
            <div style={{ minWidth: 220 }}><label>Tailor to a job (optional)</label>
              <select value={jobId} onChange={(e) => setJobId(e.target.value === "" ? "" : Number(e.target.value))}>
                <option value="">None</option>
                {jobs.data?.items.map((j) => <option key={j.id} value={j.id}>{j.title}{j.company ? ` – ${j.company}` : ""}</option>)}
              </select></div>
            <button className="btn" style={{ alignSelf: "end" }} onClick={analyze} disabled={act.busy || resumeId == null}>{act.busy ? "Analyzing…" : "Find weak bullets"}</button>
          </div>
        )}
        {(resumes.data?.items.length ?? 1) === 0 && <Empty>Upload a resume first. <Link to="/resumes">Go to resumes</Link></Empty>}
      </Card>
      {info && <div className="alert info">{info}</div>}
      {applied && <div className="alert ok">Saved as version {applied.versionNumber} (ATS score {applied.ats?.overallScore ?? "–"}). <Link to={`/resumes/${applied.resumeId}`}>Open resume</Link></div>}
      {rewrites.length > 0 && (
        <Card title={`${rewrites.length} suggestion${rewrites.length === 1 ? "" : "s"}`}
          actions={<button className="btn" disabled={act.busy || chosen.length === 0 || blocked.length > 0} onClick={apply}>Apply {chosen.length} as new version</button>}>
          {blocked.length > 0 && <div className="alert error">Replace the [X%]-style placeholders with your real numbers before applying.</div>}
          {rewrites.map((r) => (
            <div key={r.id} className="rewrite">
              <div className="row between"><label className="row" style={{ margin: 0, fontWeight: 400 }}>
                <input type="checkbox" style={{ width: "auto" }} checked={!!picked[r.id]} onChange={(e) => setPicked({ ...picked, [r.id]: e.target.checked })} /> Use this
                {r.section && <Badge>{r.section}</Badge>}</label>
                <span className="row">{r.impactScoreBefore != null && r.impactScoreAfter != null && <Badge tone="green">impact {r.impactScoreBefore} → {r.impactScoreAfter}</Badge>}
                  <button className="btn ghost small" onClick={() => reject(r.id)} disabled={act.busy}>Dismiss</button></span></div>
              <div className="was">{r.originalText}</div>
              <textarea rows={2} value={edits[r.id] ?? ""} onChange={(e) => setEdits({ ...edits, [r.id]: e.target.value })} />
              {r.needsUserMetric && <div className="hint">This bullet has no numbers. Add a real figure only if you have one; do not guess.</div>}
            </div>
          ))}
        </Card>
      )}
    </>
  );
}

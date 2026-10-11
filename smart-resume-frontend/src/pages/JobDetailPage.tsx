import { useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { api } from "../api/client";
import type { Application, GeneratedDoc, JobDescription, MatchResponse, MatchSummary, Paged, Requirement, ResumeSummary } from "../api/types";
import { Badge, Card, Empty, ErrorBox, PageHeader, Spinner, fmtDate, titleCase, useAction, useAsync } from "../components/ui";

function Reqs({ title, items }: { title: string; items: Requirement[] }) {
  if (!items || items.length === 0) return null;
  return (
    <div style={{ marginBottom: 10 }}>
      <small>{title}</small>
      <div className="row" style={{ gap: 6, marginTop: 4 }}>{items.map((r) => <Badge key={r.name} tone={r.importance === "REQUIRED" ? "blue" : "gray"}>{r.name}{r.importance === "PREFERRED" ? " (nice to have)" : ""}</Badge>)}</div>
    </div>
  );
}

export default function JobDetailPage() {
  const { id } = useParams();
  const jobId = Number(id);
  const nav = useNavigate();
  const job = useAsync(() => api.get<JobDescription>(`/api/job-descriptions/${jobId}`), [jobId]);
  const resumes = useAsync(() => api.get<Paged<ResumeSummary>>("/api/resumes?size=50"), []);
  const matches = useAsync(() => api.get<Paged<MatchSummary>>(`/api/matches?jobDescriptionId=${jobId}&size=10`), [jobId]);
  const [resumeId, setResumeId] = useState<number | "">("");
  const [github, setGithub] = useState(true);
  const [ai, setAi] = useState(true);
  const [tone, setTone] = useState("PROFESSIONAL");
  const [doc, setDoc] = useState<GeneratedDoc | null>(null);
  const [docText, setDocText] = useState("");
  const [note, setNote] = useState<string | null>(null);
  const act = useAction();

  const chosen = resumeId === "" ? resumes.data?.items[0]?.id : resumeId;

  const match = async () => {
    if (!chosen) return;
    const m = await act.run(() => api.post<MatchResponse>("/api/matches", { resumeId: chosen, jobDescriptionId: jobId, includeGithub: github, includeAi: ai }));
    if (m) nav(`/matches/${m.id}`);
  };
  const cover = async () => {
    if (!chosen) return;
    const d = await act.run(() => api.post<GeneratedDoc>("/api/optimizer/cover-letters", { resumeId: chosen, jobDescriptionId: jobId, tone }));
    if (d) { setDoc(d); setDocText(d.content); setNote(null); }
  };
  const questions = async () => {
    if (!chosen) return;
    const d = await act.run(() => api.post<GeneratedDoc>("/api/optimizer/interview-questions", { resumeId: chosen, jobDescriptionId: jobId, count: 8 }));
    if (d) { setDoc(d); setDocText(d.content); setNote(null); }
  };
  const saveDoc = async () => {
    if (!doc) return;
    const d = await act.run(() => api.put<GeneratedDoc>(`/api/documents/${doc.id}`, { title: doc.title, content: docText }));
    if (d) { setDoc(d); setNote("Saved."); }
  };
  const track = async () => {
    const a = await act.run(() => api.post<Application>(`/api/applications/from-job/${jobId}`, {}));
    if (a) nav("/tracker");
  };
  const remove = async () => {
    if (!window.confirm("Delete this job description and its matches?")) return;
    if (await act.run(async () => { await api.del(`/api/job-descriptions/${jobId}`); return true; })) nav("/job-match");
  };
  const reExtract = async () => { if (await act.run(() => api.post(`/api/job-descriptions/${jobId}/re-extract`))) job.reload(); };

  if (job.loading && !job.data) return <Spinner />;
  const j = job.data;
  if (!j) return <ErrorBox error={job.error ?? "Job not found"} />;
  const noResume = !resumes.loading && (resumes.data?.items.length ?? 0) === 0;

  return (
    <>
      <PageHeader title={j.title} subtitle={[j.company, j.location, j.seniority && titleCase(j.seniority)].filter(Boolean).join(" · ")}
        actions={<><button className="btn ghost" onClick={track} disabled={act.busy}>Add to tracker</button><button className="btn danger" onClick={remove}>Delete</button></>} />
      <ErrorBox error={act.error} />
      <div className="grid two">
        <Card title="Match against a resume">
          {noResume ? <Empty>Upload a resume first. <Link to="/resumes">Go to resumes</Link></Empty> : (
            <div className="stack">
              <div><label>Resume</label>
                <select value={chosen ?? ""} onChange={(e) => setResumeId(Number(e.target.value))}>
                  {resumes.data?.items.map((r) => <option key={r.id} value={r.id}>{r.title} (v{r.currentVersionNumber})</option>)}
                </select></div>
              <label className="row" style={{ fontWeight: 400, margin: 0 }}><input type="checkbox" style={{ width: "auto" }} checked={github} onChange={(e) => setGithub(e.target.checked)} /> Count skills from my GitHub analysis</label>
              <label className="row" style={{ fontWeight: 400, margin: 0 }}><input type="checkbox" style={{ width: "auto" }} checked={ai} onChange={(e) => setAi(e.target.checked)} /> Include AI recommendations</label>
              <button className="btn" onClick={match} disabled={act.busy || !chosen}>{act.busy ? "Working…" : "Run match"}</button>
              <hr style={{ width: "100%", borderColor: "var(--line)" }} />
              <div className="row">
                <select style={{ width: "auto" }} value={tone} onChange={(e) => setTone(e.target.value)}>
                  {["PROFESSIONAL", "ENTHUSIASTIC", "CONCISE", "FORMAL"].map((t) => <option key={t} value={t}>{titleCase(t)}</option>)}
                </select>
                <button className="btn ghost" onClick={cover} disabled={act.busy || !chosen}>Write cover letter</button>
                <button className="btn ghost" onClick={questions} disabled={act.busy || !chosen}>Interview questions</button>
              </div>
            </div>
          )}
        </Card>
        <Card title="Extracted requirements" actions={<button className="btn ghost small" onClick={reExtract} disabled={act.busy}>Re-extract</button>}>
          {j.requirements ? (<>
            <Reqs title="Hard skills" items={j.requirements.hardSkills} />
            <Reqs title="Tools" items={j.requirements.tools} />
            <Reqs title="Soft skills" items={j.requirements.softSkills} />
            <Reqs title="Certifications" items={j.requirements.certifications} />
          </>) : <Empty>No skills extracted yet.</Empty>}
        </Card>
      </div>
      {doc && (
        <Card title={doc.title} actions={doc.type === "COVER_LETTER" ? <button className="btn small" onClick={saveDoc} disabled={act.busy}>Save edits</button> : undefined}>
          {note && <div className="alert ok">{note}</div>}
          {doc.type === "COVER_LETTER" ? <textarea rows={16} value={docText} onChange={(e) => setDocText(e.target.value)} /> : (
            <div className="stack">{doc.structured?.questions?.map((q, i) => (
              <div key={i} className="rewrite"><Badge tone="blue">{titleCase(q.category)}</Badge> <b>{q.question}</b>
                <p className="muted" style={{ margin: "6px 0 2px" }}>Interviewers look for: {q.whatInterviewersLookFor}</p>
                <p style={{ margin: 0 }}>Outline: {q.answerOutline}</p></div>
            )) ?? <div className="pre">{doc.content}</div>}</div>
          )}
        </Card>
      )}
      <Card title="Previous matches">
        {matches.loading ? <Spinner /> : matches.data && matches.data.items.length > 0 ? (
          <table><tbody>{matches.data.items.map((m) => (
            <tr key={m.id}><td><Link to={`/matches/${m.id}`}>{Math.round(m.matchPercentage)}% match</Link></td><td>{fmtDate(m.createdAt)}</td></tr>
          ))}</tbody></table>
        ) : <Empty>No matches run for this job yet.</Empty>}
      </Card>
      <Card title="Job text"><div className="pre">{j.rawText}</div></Card>
    </>
  );
}

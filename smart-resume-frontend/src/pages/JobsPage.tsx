import { useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { api } from "../api/client";
import type { JobDescription, JobDescriptionSummary, Paged } from "../api/types";
import { useAuth } from "../auth/AuthContext";
import { Badge, Card, Empty, ErrorBox, PageHeader, Spinner, fmtDate, titleCase, useAction, useAsync } from "../components/ui";

export default function JobsPage() {
  const nav = useNavigate();
  const { user, requireAuth } = useAuth();
  const list = useAsync(() => (user ? api.get<Paged<JobDescriptionSummary>>("/api/job-descriptions?size=50") : Promise.resolve(null)), [user?.id]);
  const [title, setTitle] = useState("");
  const [company, setCompany] = useState("");
  const [location, setLocation] = useState("");
  const [url, setUrl] = useState("");
  const [text, setText] = useState("");
  const { busy, error, run } = useAction();

  const create = (e: FormEvent) => {
    e.preventDefault();
    requireAuth(async () => {
      const j = await run(() => api.post<JobDescription>("/api/job-descriptions", {
        title, company: company || null, location: location || null, sourceUrl: url || null, rawText: text,
      }));
      if (j) nav(`/jobs/${j.id}`);
    }, "Create a free account to save this job and match it against your resume. Your job details are kept.");
  };

  return (
    <>
      <PageHeader title="Job Match" subtitle="Paste a job description to see which skills you have, which are partly covered and which are missing, then write a tailored cover letter." />
      <Card title="Add a job description">
        <form onSubmit={create}>
          <ErrorBox error={error} />
          <div className="grid three">
            <div className="field"><label>Job title *</label><input value={title} onChange={(e) => setTitle(e.target.value)} required /></div>
            <div className="field"><label>Company</label><input value={company} onChange={(e) => setCompany(e.target.value)} /></div>
            <div className="field"><label>Location</label><input value={location} onChange={(e) => setLocation(e.target.value)} /></div>
          </div>
          <div className="field"><label>Job post URL</label><input value={url} onChange={(e) => setUrl(e.target.value)} placeholder="https://…" /></div>
          <div className="field"><label>Job description text * (at least 80 characters)</label><textarea value={text} onChange={(e) => setText(e.target.value)} required minLength={80} rows={8} /></div>
          <button className="btn" disabled={busy}>{busy ? "Extracting skills…" : "Save & extract skills"}</button>
        </form>
      </Card>
      {user && <Card title="Saved jobs">
        {list.loading ? <Spinner /> : list.error ? <ErrorBox error={list.error} /> : list.data && list.data.items.length > 0 ? (
          <table>
            <thead><tr><th>Title</th><th>Company</th><th>Level</th><th>Added</th></tr></thead>
            <tbody>{list.data.items.map((j) => (
              <tr key={j.id}><td><Link to={`/jobs/${j.id}`}>{j.title}</Link></td><td>{j.company ?? "–"}</td>
                <td>{j.seniority ? <Badge tone="blue">{titleCase(j.seniority)}</Badge> : "–"}</td><td>{fmtDate(j.createdAt)}</td></tr>
            ))}</tbody>
          </table>
        ) : <Empty>No jobs yet.</Empty>}
      </Card>}
    </>
  );
}

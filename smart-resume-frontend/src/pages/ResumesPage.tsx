import { useRef, useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { api } from "../api/client";
import type { Paged, ResumeDetail, ResumeSummary } from "../api/types";
import { Badge, Card, Empty, ErrorBox, PageHeader, ScoreRing, Spinner, fmtDate, useAction, useAsync } from "../components/ui";

export default function ResumesPage() {
  const nav = useNavigate();
  const [archived, setArchived] = useState(false);
  const list = useAsync(() => api.get<Paged<ResumeSummary>>(`/api/resumes?size=50&includeArchived=${archived}`), [archived]);
  const fileRef = useRef<HTMLInputElement>(null);
  const [title, setTitle] = useState("");
  const [role, setRole] = useState("");
  const { busy, error, run } = useAction();

  const upload = async (e: FormEvent) => {
    e.preventDefault();
    const file = fileRef.current?.files?.[0];
    if (!file) return;
    const form = new FormData();
    form.append("file", file);
    if (title.trim()) form.append("title", title.trim());
    if (role.trim()) form.append("targetRole", role.trim());
    const created = await run(() => api.upload<ResumeDetail>("/api/resumes", form));
    if (created) nav(`/resumes/${created.resume.id}`);
  };

  return (
    <>
      <PageHeader title="Resumes" subtitle="Every upload and edit becomes a version you can compare or restore." />
      <Card title="Upload a resume">
        <form onSubmit={upload}>
          <ErrorBox error={error} />
          <div className="grid three">
            <div className="field"><label>PDF or DOCX file</label><input ref={fileRef} type="file" accept=".pdf,.docx,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document" required /></div>
            <div className="field"><label>Title (optional)</label><input value={title} onChange={(e) => setTitle(e.target.value)} placeholder="e.g. Backend resume" /></div>
            <div className="field"><label>Target role (optional)</label><input value={role} onChange={(e) => setRole(e.target.value)} placeholder="e.g. Java Developer" /></div>
          </div>
          <button className="btn" disabled={busy}>{busy ? "Analyzing…" : "Upload & analyze"}</button>
        </form>
      </Card>
      <Card title="Your resumes" actions={<label className="row" style={{ margin: 0, fontWeight: 400 }}><input type="checkbox" style={{ width: "auto" }} checked={archived} onChange={(e) => setArchived(e.target.checked)} /> Show archived</label>}>
        {list.loading ? <Spinner /> : list.error ? <ErrorBox error={list.error} /> : list.data && list.data.items.length > 0 ? (
          <table>
            <thead><tr><th>Title</th><th>Target role</th><th>Version</th><th>Updated</th><th>ATS</th></tr></thead>
            <tbody>{list.data.items.map((r) => (
              <tr key={r.id}>
                <td><Link to={`/resumes/${r.id}`}>{r.title}</Link> {r.archived && <Badge>archived</Badge>}</td>
                <td>{r.targetRole ?? "–"}</td><td>v{r.currentVersionNumber ?? "–"}</td><td>{fmtDate(r.updatedAt)}</td>
                <td style={{ width: 70 }}><ScoreRing score={r.atsScore} size={52} /></td>
              </tr>
            ))}</tbody>
          </table>
        ) : <Empty>No resumes yet. Upload one above.</Empty>}
      </Card>
    </>
  );
}

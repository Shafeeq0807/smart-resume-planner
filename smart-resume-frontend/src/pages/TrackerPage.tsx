import { useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { api } from "../api/client";
import type { AppStatus, Application, Board } from "../api/types";
import { Badge, ErrorBox, PageHeader, Spinner, fmtDate, titleCase, useAction, useAsync } from "../components/ui";

const STATUSES: AppStatus[] = ["SAVED", "APPLIED", "INTERVIEWING", "OFFERED", "REJECTED"];
const toDateInput = (iso: string | null) => (iso ? iso.slice(0, 10) : "");
const toIso = (d: string) => (d ? new Date(d + "T09:00:00Z").toISOString() : null);

export default function TrackerPage() {
  const board = useAsync(() => api.get<Board>("/api/applications/board"), []);
  const [dragId, setDragId] = useState<number | null>(null);
  const [over, setOver] = useState<AppStatus | null>(null);
  const [editing, setEditing] = useState<Application | "new" | null>(null);
  const act = useAction();

  const move = async (app: Application, status: AppStatus, position: number) => {
    await act.run(() => api.post(`/api/applications/${app.id}/move`, { status, position, version: app.version }));
    board.reload();
  };

  const onDrop = (status: AppStatus) => {
    setOver(null);
    const col = board.data?.columns.find((c) => c.status === status);
    const app = board.data?.columns.flatMap((c) => c.applications).find((a) => a.id === dragId);
    setDragId(null);
    if (!app || !col) return;
    const position = col.applications.filter((a) => a.id !== app.id).length;
    if (app.status === status) return;
    void move(app, status, position);
  };

  return (
    <>
      <PageHeader title="Application tracker" subtitle="Drag cards between columns as your applications progress."
        actions={<button className="btn" onClick={() => setEditing("new")}>Add application</button>} />
      <ErrorBox error={act.error ?? board.error} />
      {board.loading && !board.data ? <Spinner /> : (
        <div className="board">
          {board.data?.columns.map((col) => (
            <div key={col.status} className={`col ${over === col.status ? "over" : ""}`}
              onDragOver={(e) => { e.preventDefault(); setOver(col.status); }} onDragLeave={() => setOver(null)} onDrop={() => onDrop(col.status)}>
              <h4>{titleCase(col.status)} <Badge>{col.count}</Badge></h4>
              {col.applications.map((a) => (
                <div key={a.id} className={`appcard ${dragId === a.id ? "dragging" : ""}`} draggable
                  onDragStart={() => setDragId(a.id)} onDragEnd={() => setDragId(null)} onClick={() => setEditing(a)}>
                  <b>{a.companyName}</b>
                  <span>{a.positionTitle}</span>
                  <small>{[a.location, a.salaryRange].filter(Boolean).join(" · ")}</small>
                  {a.nextFollowUpAt && <div><Badge tone="amber">Follow up {fmtDate(a.nextFollowUpAt)}</Badge></div>}
                </div>
              ))}
            </div>
          ))}
        </div>
      )}
      {editing && <AppModal app={editing === "new" ? null : editing} onClose={() => setEditing(null)} onSaved={() => { setEditing(null); board.reload(); }} />}
    </>
  );
}

function AppModal({ app, onClose, onSaved }: { app: Application | null; onClose: () => void; onSaved: () => void }) {
  const full = useAsync(() => (app ? api.get<Application>(`/api/applications/${app.id}`) : Promise.resolve(null)), [app?.id]);
  const a = full.data ?? app;
  const [form, setForm] = useState({
    companyName: app?.companyName ?? "", positionTitle: app?.positionTitle ?? "", jobUrl: app?.jobUrl ?? "", location: app?.location ?? "",
    salaryRange: app?.salaryRange ?? "", notes: app?.notes ?? "", appliedAt: toDateInput(app?.appliedAt ?? null),
    nextFollowUpAt: toDateInput(app?.nextFollowUpAt ?? null), status: (app?.status ?? "SAVED") as AppStatus,
  });
  const set = (k: keyof typeof form) => (e: { target: { value: string } }) => setForm({ ...form, [k]: e.target.value });
  const act = useAction();

  const save = async (e: FormEvent) => {
    e.preventDefault();
    const body = {
      companyName: form.companyName, positionTitle: form.positionTitle, jobUrl: form.jobUrl || null, location: form.location || null,
      salaryRange: form.salaryRange || null, notes: form.notes || null, appliedAt: toIso(form.appliedAt), nextFollowUpAt: toIso(form.nextFollowUpAt),
      jobDescriptionId: a?.jobDescriptionId ?? null, resumeVersionId: a?.resumeVersionId ?? null,
    };
    const ok = await act.run(async () => {
      if (a) await api.put(`/api/applications/${a.id}`, { ...body, version: a.version });
      else await api.post("/api/applications", { ...body, status: form.status });
      return true;
    });
    if (ok) onSaved();
  };
  const remove = async () => {
    if (!a || !window.confirm("Delete this application?")) return;
    if (await act.run(async () => { await api.del(`/api/applications/${a.id}`); return true; })) onSaved();
  };

  return (
    <div className="modal-bg" onClick={onClose}>
      <form className="modal" onClick={(e) => e.stopPropagation()} onSubmit={save}>
        <h2>{a ? "Edit application" : "New application"}</h2>
        <ErrorBox error={act.error} />
        <div className="grid two" style={{ gap: 10 }}>
          <div className="field"><label>Company *</label><input value={form.companyName} onChange={set("companyName")} required /></div>
          <div className="field"><label>Position *</label><input value={form.positionTitle} onChange={set("positionTitle")} required /></div>
          <div className="field"><label>Location</label><input value={form.location} onChange={set("location")} /></div>
          <div className="field"><label>Salary range</label><input value={form.salaryRange} onChange={set("salaryRange")} /></div>
          <div className="field"><label>Applied on</label><input type="date" value={form.appliedAt} onChange={set("appliedAt")} /></div>
          <div className="field"><label>Follow up on</label><input type="date" value={form.nextFollowUpAt} onChange={set("nextFollowUpAt")} /></div>
        </div>
        {!a && <div className="field"><label>Column</label><select value={form.status} onChange={set("status")}>{STATUSES.map((s) => <option key={s} value={s}>{titleCase(s)}</option>)}</select></div>}
        <div className="field"><label>Job link</label><input value={form.jobUrl} onChange={set("jobUrl")} placeholder="https://…" /></div>
        <div className="field"><label>Notes</label><textarea rows={3} value={form.notes} onChange={set("notes")} /></div>
        {a?.jobDescriptionId && <p><Link to={`/jobs/${a.jobDescriptionId}`}>Open linked job description</Link></p>}
        {full.data?.history && full.data.history.length > 0 && (
          <div><small>History</small>{full.data.history.map((h, i) => <div key={i}><small>{fmtDate(h.changedAt)}: {h.from ? titleCase(h.from) + " → " : ""}{titleCase(h.to)}</small></div>)}</div>
        )}
        <div className="row between" style={{ marginTop: 14 }}>
          <span>{a && <button type="button" className="btn danger" onClick={remove}>Delete</button>}</span>
          <span className="row"><button type="button" className="btn ghost" onClick={onClose}>Cancel</button><button className="btn" disabled={act.busy}>Save</button></span>
        </div>
      </form>
    </div>
  );
}

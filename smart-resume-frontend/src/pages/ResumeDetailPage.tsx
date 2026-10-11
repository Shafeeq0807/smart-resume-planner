import { useEffect, useRef, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { api, download } from "../api/client";
import type { AtsResult, CompareResponse, ExportRecord, ResumeDetail, Template, VersionDetail, VersionSummary } from "../api/types";
import { Badge, Bar, Card, Empty, ErrorBox, PageHeader, ScoreRing, Spinner, fmtDate, titleCase, useAction, useAsync } from "../components/ui";

type Tab = "ats" | "text" | "versions" | "export";

export default function ResumeDetailPage() {
  const { id } = useParams();
  const resumeId = Number(id);
  const nav = useNavigate();
  const detail = useAsync(() => api.get<ResumeDetail>(`/api/resumes/${resumeId}`), [resumeId]);
  const [tab, setTab] = useState<Tab>("ats");
  const [selected, setSelected] = useState<number | null>(null);
  const act = useAction();

  const resume = detail.data?.resume;
  const versions = [...(detail.data?.versions ?? [])].sort((a, b) => a.versionNumber - b.versionNumber); // oldest first
  const activeId = selected ?? resume?.currentVersionId ?? null;

  if (detail.loading && !detail.data) return <Spinner />;
  if (detail.error || !detail.data || !resume) return <ErrorBox error={detail.error ?? "Resume not found"} />;

  const refresh = (versionId?: number) => { if (versionId) setSelected(versionId); detail.reload(); };

  const rename = async () => {
    const title = window.prompt("Resume title", resume.title);
    if (title && title.trim()) { await act.run(() => api.patch(`/api/resumes/${resumeId}`, { title: title.trim() })); detail.reload(); }
  };
  const setRole = async () => {
    const role = window.prompt("Target role", resume.targetRole ?? "");
    if (role !== null) { await act.run(() => api.patch(`/api/resumes/${resumeId}`, { targetRole: role })); detail.reload(); }
  };
  const toggleArchive = async () => { await act.run(() => api.patch(`/api/resumes/${resumeId}`, { archived: !resume.archived })); detail.reload(); };
  const remove = async () => {
    if (!window.confirm("Delete this resume and all of its versions? This cannot be undone.")) return;
    const done = await act.run(async () => { await api.del(`/api/resumes/${resumeId}`); return true; });
    if (done) nav("/resumes");
  };

  return (
    <>
      <PageHeader
        title={resume.title}
        subtitle={`${resume.targetRole ?? "No target role"} · ${versions.length} version${versions.length === 1 ? "" : "s"} · updated ${fmtDate(resume.updatedAt)}`}
        actions={<>
          <Link className="btn ghost" to={`/optimizer?resumeId=${resumeId}`}>Improve bullets</Link>
          <button className="btn ghost" onClick={rename}>Rename</button>
          <button className="btn ghost" onClick={setRole}>Target role</button>
          <button className="btn ghost" onClick={toggleArchive}>{resume.archived ? "Unarchive" : "Archive"}</button>
          <button className="btn danger" onClick={remove}>Delete</button>
        </>} />
      <ErrorBox error={act.error} />
      <div className="row" style={{ marginBottom: 14 }}>
        <label style={{ margin: 0 }}>Viewing version</label>
        <select style={{ width: "auto" }} value={activeId ?? ""} onChange={(e) => setSelected(Number(e.target.value))}>
          {[...versions].reverse().map((v) => <option key={v.id} value={v.id}>v{v.versionNumber} · {v.label ?? v.source}{v.current ? " (current)" : ""}</option>)}
        </select>
      </div>
      <div className="tabs">
        {([["ats", "ATS score"], ["text", "Content & edit"], ["versions", "Versions & compare"], ["export", "Export PDF"]] as const).map(([k, label]) => (
          <button key={k} className={tab === k ? "active" : ""} onClick={() => setTab(k)}>{label}</button>
        ))}
      </div>
      {activeId && tab === "ats" && <AtsTab resumeId={resumeId} versionId={activeId} />}
      {activeId && tab === "text" && <TextTab resumeId={resumeId} versionId={activeId} onSaved={refresh} />}
      {tab === "versions" && <VersionsTab resumeId={resumeId} versions={versions} onChanged={refresh} />}
      {activeId && tab === "export" && <ExportTab resumeId={resumeId} versionId={activeId} />}
    </>
  );
}

function AtsTab({ resumeId, versionId }: { resumeId: number; versionId: number }) {
  const ats = useAsync(() => api.get<AtsResult>(`/api/resumes/${resumeId}/versions/${versionId}/ats`), [resumeId, versionId]);
  const [override, setOverride] = useState<AtsResult | null>(null);
  const act = useAction();
  useEffect(() => setOverride(null), [versionId]);
  const result = override ?? ats.data;

  const rerun = async (ai: boolean) => {
    const r = await act.run(() => api.post<AtsResult>(`/api/resumes/${resumeId}/versions/${versionId}/ats?ai=${ai}`));
    if (r) setOverride(r);
  };
  if (ats.loading) return <Spinner />;
  if (!result) return <><ErrorBox error={ats.error} /><button className="btn" onClick={() => rerun(false)}>Run ATS analysis</button></>;

  return (
    <>
      <ErrorBox error={act.error} />
      <div className="grid two">
        <Card title="ATS readiness" actions={<>
          <button className="btn ghost small" disabled={act.busy} onClick={() => rerun(false)}>Re-run</button>
          <button className="btn small" disabled={act.busy} onClick={() => rerun(true)}>{act.busy ? "Working…" : "Add AI summary"}</button></>}>
          <div className="row" style={{ gap: 24 }}>
            <ScoreRing score={result.overallScore} size={120} label={result.grade} />
            <div className="stack" style={{ flex: 1, minWidth: 180 }}>
              {result.categories.map((c) => (
                <div key={c.name}><div className="row between"><small>{titleCase(c.name)}</small><small>{c.score}/{c.max}</small></div><Bar value={c.score} max={c.max} /></div>
              ))}
            </div>
          </div>
          {result.detectedTitle && <p className="muted">Detected headline: <b>{result.detectedTitle}</b></p>}
          <p>{result.detectedSections.map((s) => <Badge key={s} tone="blue">{titleCase(s)}</Badge>)}</p>
          {result.aiSummary && <div className="alert info">{result.aiSummary}</div>}
        </Card>
        <Card title={`Findings (${result.issues.length})`}>
          {result.issues.length === 0 ? <Empty>No issues found.</Empty> : result.issues.map((i, n) => (
            <div key={n} className={`issue ${i.severity}`}>
              <Badge tone={i.severity === "ERROR" ? "red" : i.severity === "WARNING" ? "amber" : "blue"}>{i.severity}</Badge> {i.message}
              {i.suggestion && <small>→ {i.suggestion}</small>}
            </div>
          ))}
        </Card>
      </div>
    </>
  );
}

function TextTab({ resumeId, versionId, onSaved }: { resumeId: number; versionId: number; onSaved: (id?: number) => void }) {
  const version = useAsync(() => api.get<VersionDetail>(`/api/resumes/${resumeId}/versions/${versionId}`), [resumeId, versionId]);
  const [text, setText] = useState("");
  const [label, setLabel] = useState("");
  const fileRef = useRef<HTMLInputElement>(null);
  const act = useAction();
  const [note, setNote] = useState<string | null>(null);
  useEffect(() => { if (version.data) setText(version.data.extractedText); }, [version.data]);

  const save = async () => {
    const v = await act.run(() => api.post<VersionDetail>(`/api/resumes/${resumeId}/versions/edit`, { text, label: label.trim() || undefined }));
    if (v) { setLabel(""); setNote(`Saved as version ${v.versionNumber}. Its ATS score is ${v.ats?.overallScore ?? "–"}.`); onSaved(v.id); }
  };
  const uploadNew = async () => {
    const file = fileRef.current?.files?.[0];
    if (!file) return;
    const form = new FormData();
    form.append("file", file);
    if (label.trim()) form.append("label", label.trim());
    const v = await act.run(() => api.upload<VersionDetail>(`/api/resumes/${resumeId}/versions`, form));
    if (v) { setNote(`Uploaded as version ${v.versionNumber}.`); if (fileRef.current) fileRef.current.value = ""; onSaved(v.id); }
  };

  if (version.loading) return <Spinner />;
  const v = version.data;
  return (
    <div className="grid two">
      <Card title="Resume text" actions={v?.hasOriginalFile ? <button className="btn ghost small" onClick={() => download(`/api/resumes/${resumeId}/versions/${versionId}/file`, v.originalFilename ?? "resume")}>Download original</button> : undefined}>
        <ErrorBox error={act.error} />
        {note && <div className="alert ok">{note}</div>}
        <textarea className="mono" value={text} onChange={(e) => setText(e.target.value)} spellCheck={false} />
        <div className="hint">Edit freely. Saving never overwrites: it creates a new version and re-scores it. Section titles such as EXPERIENCE should stay on their own line.</div>
        <div className="row" style={{ marginTop: 10 }}>
          <input style={{ maxWidth: 240 }} placeholder="Version label (optional)" value={label} onChange={(e) => setLabel(e.target.value)} />
          <button className="btn" disabled={act.busy || text.trim().length < 50} onClick={save}>{act.busy ? "Saving…" : "Save as new version"}</button>
        </div>
      </Card>
      <Card title="Upload a new file as a version">
        <p className="muted">Replace your resume with an updated PDF or DOCX. The old one stays in the history.</p>
        <input ref={fileRef} type="file" accept=".pdf,.docx" />
        <div style={{ marginTop: 10 }}><button className="btn" disabled={act.busy} onClick={uploadNew}>Upload version</button></div>
        {v && <dl className="kv" style={{ marginTop: 16 }}>
          <dt>Source</dt><dd>{titleCase(v.source)}</dd>
          <dt>Words</dt><dd>{v.wordCount ?? "–"}</dd>
          <dt>Pages</dt><dd>{v.pageCount ?? "–"}</dd>
          <dt>Created</dt><dd>{fmtDate(v.createdAt)}</dd>
        </dl>}
      </Card>
    </div>
  );
}

function VersionsTab({ resumeId, versions, onChanged }: { resumeId: number; versions: VersionSummary[]; onChanged: (id?: number) => void }) {
  const [from, setFrom] = useState<number | null>(versions.length > 1 ? versions[versions.length - 2].id : null);
  const [to, setTo] = useState<number | null>(versions.length ? versions[versions.length - 1].id : null);
  const [diff, setDiff] = useState<CompareResponse | null>(null);
  const act = useAction();

  const compare = async () => {
    if (from == null || to == null) return;
    const d = await act.run(() => api.get<CompareResponse>(`/api/resumes/${resumeId}/compare?from=${from}&to=${to}`));
    if (d) setDiff(d);
  };
  const activate = async (id: number) => { const v = await act.run(() => api.post(`/api/resumes/${resumeId}/versions/${id}/activate`)); if (v) onChanged(id); };

  return (
    <>
      <ErrorBox error={act.error} />
      <Card title="History">
        <table>
          <thead><tr><th>Version</th><th>Label</th><th>Source</th><th>Words</th><th>ATS</th><th>Created</th><th /></tr></thead>
          <tbody>{[...versions].reverse().map((v) => (
            <tr key={v.id}>
              <td>v{v.versionNumber} {v.current && <Badge tone="green">current</Badge>}</td>
              <td>{v.label ?? "–"}</td><td>{titleCase(v.source)}</td><td>{v.wordCount ?? "–"}</td>
              <td>{v.atsScore != null ? <Badge tone={v.atsScore >= 75 ? "green" : v.atsScore >= 50 ? "amber" : "red"}>{v.atsScore}</Badge> : "–"}</td>
              <td>{fmtDate(v.createdAt)}</td>
              <td>{!v.current && <button className="btn ghost small" disabled={act.busy} onClick={() => activate(v.id)}>Make current</button>}</td>
            </tr>
          ))}</tbody>
        </table>
      </Card>
      {versions.length > 1 && (
        <Card title="Compare two versions">
          <div className="row">
            <select style={{ width: "auto" }} value={from ?? ""} onChange={(e) => setFrom(Number(e.target.value))}>{versions.map((v) => <option key={v.id} value={v.id}>v{v.versionNumber}</option>)}</select>
            <span>→</span>
            <select style={{ width: "auto" }} value={to ?? ""} onChange={(e) => setTo(Number(e.target.value))}>{versions.map((v) => <option key={v.id} value={v.id}>v{v.versionNumber}</option>)}</select>
            <button className="btn" disabled={act.busy || from === to} onClick={compare}>Compare</button>
          </div>
          {diff && (
            <div style={{ marginTop: 14 }}>
              <p>
                <Badge tone="green">+{diff.addedLines} added</Badge> <Badge tone="red">−{diff.removedLines} removed</Badge> <Badge>{diff.unchangedLines} unchanged</Badge>{" "}
                ATS {diff.fromAtsScore ?? "–"} → <b>{diff.toAtsScore ?? "–"}</b>
                {diff.wordCountDelta != null && <> · words {diff.wordCountDelta >= 0 ? "+" : ""}{diff.wordCountDelta}</>}
              </p>
              <div className="diff">
                {diff.rows.filter((r) => r.type !== "EQUAL").map((r, i) => (
                  <div key={i} className={`r ${r.type}`}><div className="L">{r.left ?? ""}</div><div className="R">{r.right ?? ""}</div></div>
                ))}
                {diff.rows.every((r) => r.type === "EQUAL") && <Empty>These versions are identical.</Empty>}
              </div>
            </div>
          )}
        </Card>
      )}
    </>
  );
}

function ExportTab({ resumeId, versionId }: { resumeId: number; versionId: number }) {
  const templates = useAsync(() => api.get<Template[]>("/api/templates"), []);
  const history = useAsync(() => api.get<{ items: ExportRecord[] }>("/api/exports?size=10"), []);
  const [code, setCode] = useState("classic");
  const [accent, setAccent] = useState("#1f4e79");
  const [font, setFont] = useState(10.5);
  const [margin, setMargin] = useState(18);
  const act = useAction();
  const [last, setLast] = useState<ExportRecord | null>(null);

  const make = async () => {
    const rec = await act.run(() => api.post<ExportRecord>("/api/exports", { resumeId, versionId, templateCode: code, settings: { accentColor: accent, fontSizePt: font, marginMm: margin } }));
    if (rec) { setLast(rec); history.reload(); if (rec.downloadUrl) await act.run(() => download(rec.downloadUrl!, "resume.pdf")); }
  };

  return (
    <div className="grid two">
      <Card title="Create a PDF">
        <ErrorBox error={act.error} />
        {templates.loading ? <Spinner /> : (
          <div className="stack">
            {templates.data?.map((t) => (
              <label key={t.code} className="row" style={{ fontWeight: 400, cursor: "pointer", margin: 0 }}>
                <input type="radio" style={{ width: "auto" }} checked={code === t.code} onChange={() => setCode(t.code)} />
                <span><b>{t.name}</b> {t.atsSafe && <Badge tone="green">ATS-safe</Badge>}<br /><small>{t.description}</small></span>
              </label>
            ))}
            <div className="grid three">
              <div><label>Accent colour</label><input type="color" value={accent} onChange={(e) => setAccent(e.target.value)} style={{ padding: 2, height: 38 }} /></div>
              <div><label>Font size (pt)</label><input type="number" min={9} max={12.5} step={0.5} value={font} onChange={(e) => setFont(Number(e.target.value))} /></div>
              <div><label>Margin (mm)</label><input type="number" min={10} max={25} step={1} value={margin} onChange={(e) => setMargin(Number(e.target.value))} /></div>
            </div>
            <button className="btn" disabled={act.busy} onClick={make}>{act.busy ? "Rendering…" : "Generate & download"}</button>
            {last && <div className="alert ok">{last.pageCount} page(s) · text-extraction self-check {last.atsSelfCheckScore ?? "–"}/100</div>}
          </div>
        )}
      </Card>
      <Card title="Recent exports">
        {history.loading ? <Spinner /> : history.data && history.data.items.length > 0 ? (
          <table><tbody>{history.data.items.map((e) => (
            <tr key={e.id}><td>{titleCase(e.templateCode)}<br /><small>{fmtDate(e.createdAt)} · {e.pageCount ?? "?"} p</small></td>
              <td><Badge tone={e.status === "COMPLETED" ? "green" : e.status === "FAILED" ? "red" : "gray"}>{e.status}</Badge></td>
              <td>{e.downloadUrl && <button className="btn ghost small" onClick={() => download(e.downloadUrl!, "resume.pdf")}>Download</button>}</td></tr>
          ))}</tbody></table>
        ) : <Empty>No exports yet.</Empty>}
      </Card>
    </div>
  );
}

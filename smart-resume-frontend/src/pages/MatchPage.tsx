import { Link, useParams } from "react-router-dom";
import { api } from "../api/client";
import type { MatchResponse } from "../api/types";
import { Badge, Bar, Card, Empty, ErrorBox, PageHeader, ScoreRing, Spinner, titleCase, useAsync } from "../components/ui";

const tone = { MATCHED: "green", PARTIAL: "amber", MISSING: "red" } as const;

export default function MatchPage() {
  const { id } = useParams();
  const m = useAsync(() => api.get<MatchResponse>(`/api/matches/${id}`), [id]);
  if (m.loading) return <Spinner />;
  const d = m.data;
  if (!d) return <ErrorBox error={m.error ?? "Match not found"} />;
  const parts: [string, number][] = [["Hard skills", d.hardSkillCoverage], ["Soft skills", d.softSkillCoverage], ["Semantic similarity", d.semanticScore], ["Title fit", d.titleFitScore]];

  return (
    <>
      <PageHeader title={`${d.jobTitle}${d.company ? " at " + d.company : ""}`} subtitle={`Resume version v${d.resumeVersionNumber}`}
        actions={<><Link className="btn ghost" to={`/jobs/${d.jobDescriptionId}`}>Job</Link><Link className="btn" to={`/optimizer?resumeId=${d.resumeId}&jobId=${d.jobDescriptionId}`}>Improve bullets for this job</Link></>} />
      <div className="grid two">
        <Card title="Overall fit">
          <div className="row" style={{ gap: 24 }}>
            <ScoreRing score={d.matchPercentage} size={120} label={titleCase(d.verdict)} />
            <div className="stack" style={{ flex: 1, minWidth: 180 }}>
              {parts.map(([label, v]) => <div key={label}><div className="row between"><small>{label}</small><small>{Math.round(v)}%</small></div><Bar value={v} /></div>)}
            </div>
          </div>
          <p style={{ marginTop: 14 }}><Badge tone="green">{d.counts.matched} matched</Badge> <Badge tone="amber">{d.counts.partial} partial</Badge> <Badge tone="red">{d.counts.missing} missing</Badge></p>
        </Card>
        <Card title="What to do next">
          {d.missingRequired.length > 0 && <div className="alert error">Missing required: {d.missingRequired.map((s) => s.skill).join(", ")}</div>}
          {d.recommendations.length > 0 ? <ul style={{ margin: 0, paddingLeft: 18 }} className="stack">{d.recommendations.map((r, i) => <li key={i}>{r}</li>)}</ul>
            : <Empty>No AI recommendations were generated for this match.</Empty>}
        </Card>
      </div>
      <Card title="Skill by skill">
        {d.skills.map((s) => (
          <div key={s.skill} className="skillrow">
            <b>{s.skill}</b>
            <span><Badge tone={tone[s.status]}>{s.status}</Badge></span>
            <span><small>{s.importance === "REQUIRED" ? "Required" : "Preferred"}{s.evidence ? ` · ${s.evidenceSource ? titleCase(s.evidenceSource) + ": " : ""}${s.evidence}` : ""}</small></span>
          </div>
        ))}
      </Card>
    </>
  );
}

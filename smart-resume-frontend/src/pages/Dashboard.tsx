import { Link } from "react-router-dom";
import { api } from "../api/client";
import type { Paged, ResumeSummary, TrackerStats, MatchSummary, Application } from "../api/types";
import { useAuth } from "../auth/AuthContext";
import { Badge, Card, Empty, ErrorBox, PageHeader, ScoreRing, Spinner, fmtDate, useAsync } from "../components/ui";

export default function Dashboard() {
  const { user } = useAuth();
  const resumes = useAsync(() => api.get<Paged<ResumeSummary>>("/api/resumes?size=5"), []);
  const stats = useAsync(() => api.get<TrackerStats>("/api/applications/stats"), []);
  const matches = useAsync(() => api.get<Paged<MatchSummary>>("/api/matches?size=5"), []);
  const followUps = useAsync(() => api.get<Application[]>("/api/applications/follow-ups?days=7"), []);

  return (
    <>
      <PageHeader title={`Hi ${user?.fullName.split(" ")[0] ?? ""}`} subtitle="Here is where your job search stands."
        actions={<Link className="btn" to="/resumes">Upload a resume</Link>} />
      <ErrorBox error={resumes.error ?? stats.error} />
      <div className="grid three">
        <Card title="Applications">
          {stats.loading ? <Spinner /> : (
            <div className="stack">
              <div className="row between"><span>Total tracked</span><b>{stats.data?.total ?? 0}</b></div>
              <div className="row between"><span>Active</span><b>{stats.data?.active ?? 0}</b></div>
              <div className="row between"><span>Interview rate</span><b>{Math.round(stats.data?.interviewRatePercent ?? 0)}%</b></div>
              <div className="row between"><span>Offer rate</span><b>{Math.round(stats.data?.offerRatePercent ?? 0)}%</b></div>
              <Link to="/tracker">Open board →</Link>
            </div>
          )}
        </Card>
        <Card title="Follow-ups due (7 days)">
          {followUps.loading ? <Spinner /> : followUps.data && followUps.data.length > 0 ? (
            <div className="stack">{followUps.data.map((a) => (
              <div key={a.id} className="row between"><span><b>{a.companyName}</b><br /><small>{a.positionTitle}</small></span><Badge tone="amber">{fmtDate(a.nextFollowUpAt)}</Badge></div>
            ))}</div>
          ) : <Empty>Nothing due. Set follow-up dates on the tracker.</Empty>}
        </Card>
        <Card title="Quick start">
          <ol style={{ margin: 0, paddingLeft: 18 }} className="stack">
            <li><Link to="/resumes">Upload your resume</Link> and read the ATS score.</li>
            <li><Link to="/job-match">Paste a job description</Link> to see your match.</li>
            <li><Link to="/optimizer">Strengthen weak bullets</Link> with AI.</li>
            <li><Link to="/tracker">Track</Link> every application.</li>
          </ol>
        </Card>
      </div>
      <div className="grid two">
        <Card title="Your resumes" actions={<Link to="/resumes">All</Link>}>
          {resumes.loading ? <Spinner /> : resumes.data && resumes.data.items.length > 0 ? (
            <table><tbody>{resumes.data.items.map((r) => (
              <tr key={r.id}><td><Link to={`/resumes/${r.id}`}>{r.title}</Link><br /><small>{r.targetRole ?? "No target role"}</small></td>
                <td style={{ width: 80 }}><ScoreRing score={r.atsScore} size={56} /></td></tr>
            ))}</tbody></table>
          ) : <Empty>No resumes yet.</Empty>}
        </Card>
        <Card title="Recent matches" actions={<Link to="/job-match">Jobs</Link>}>
          {matches.loading ? <Spinner /> : matches.data && matches.data.items.length > 0 ? (
            <table><tbody>{matches.data.items.map((m) => (
              <tr key={m.id}><td><Link to={`/matches/${m.id}`}>{m.jobTitle}</Link><br /><small>{m.company ?? ""} · {fmtDate(m.createdAt)}</small></td>
                <td style={{ textAlign: "right" }}><Badge tone={m.matchPercentage >= 70 ? "green" : m.matchPercentage >= 50 ? "amber" : "red"}>{Math.round(m.matchPercentage)}%</Badge></td></tr>
            ))}</tbody></table>
          ) : <Empty>Run a match from a job description.</Empty>}
        </Card>
      </div>
      
    </>
  );
}

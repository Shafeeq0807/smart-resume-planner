import { useEffect, useRef, useState, type DragEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { api } from "../api/client";
import type { ResumeDetail } from "../api/types";
import { useAuth } from "../auth/AuthContext";
import { TOOLS } from "../components/Layout";
import { Bar, ErrorBox, ScoreRing, useAction } from "../components/ui";

const MAX_BYTES = 10 * 1024 * 1024;
const ACCEPT = ".pdf,.docx";
const SCAN_STEPS = ["Reading your file", "Finding your sections", "Checking formatting", "Scoring your content", "Preparing your report"];

function validate(f: File): string | null {
  const name = f.name.toLowerCase();
  if (!name.endsWith(".pdf") && !name.endsWith(".docx")) return "Please choose a PDF or DOCX file.";
  if (f.size > MAX_BYTES) return "That file is larger than 10 MB.";
  return null;
}

function UploadCard() {
  const { requireAuth } = useAuth();
  const nav = useNavigate();
  const input = useRef<HTMLInputElement>(null);
  const [file, setFile] = useState<File | null>(null);
  const [drag, setDrag] = useState(false);
  const [problem, setProblem] = useState<string | null>(null);
  const [step, setStep] = useState(0);
  const { busy, error, run } = useAction();

  useEffect(() => {
    if (!busy) return;
    setStep(0);
    const t = window.setInterval(() => setStep((s) => Math.min(s + 1, SCAN_STEPS.length - 1)), 900);
    return () => window.clearInterval(t);
  }, [busy]);

  const pick = (f: File | undefined | null) => {
    if (!f) return;
    const err = validate(f);
    setProblem(err);
    setFile(err ? null : f);
  };
  const onDrop = (e: DragEvent) => { e.preventDefault(); setDrag(false); pick(e.dataTransfer.files?.[0]); };

  const scan = (f: File) => {
    requireAuth(async () => {
      const form = new FormData();
      form.append("file", f);
      const created = await run(() => api.upload<ResumeDetail>("/api/resumes", form));
      if (created) nav(`/resumes/${created.resume.id}`);
    }, "Create a free account to see your ATS score. Your file is ready and will be scanned right after.");
  };

  return (
    <div className="upload-card" id="upload">
      {busy ? (
        <div className="scanning" aria-live="polite">
          <div className="scan-doc"><span /><span /><span /><span /><i /></div>
          <h3>Scanning your resume…</h3>
          <ul>{SCAN_STEPS.map((s, i) => <li key={s} className={i < step ? "done" : i === step ? "now" : ""}>{s}</li>)}</ul>
        </div>
      ) : (
        <>
          <div className={`dropzone ${drag ? "drag" : ""} ${file ? "has-file" : ""}`}
            onDragOver={(e) => { e.preventDefault(); setDrag(true); }} onDragLeave={() => setDrag(false)} onDrop={onDrop}
            onClick={() => input.current?.click()} role="button" tabIndex={0}
            onKeyDown={(e) => { if (e.key === "Enter" || e.key === " ") input.current?.click(); }}>
            <input ref={input} type="file" accept={ACCEPT} hidden onChange={(e) => pick(e.target.files?.[0])} />
            <svg width="44" height="44" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
              <path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z" /><path d="M14 3v5h5" /><path d="M12 17v-6M9.5 13.5 12 11l2.5 2.5" />
            </svg>
            {file ? (
              <><b className="file-name">{file.name}</b><small>{(file.size / 1024).toFixed(0)} KB · click to change</small></>
            ) : (
              <><b>Drag & drop your resume here</b><small>or <u>browse your files</u></small></>
            )}
          </div>
          <ErrorBox error={problem ?? error} />
          <button className="btn block lg" disabled={!file} onClick={() => file && scan(file)}>Check my score</button>
          <p className="fine">PDF or DOCX, up to 10 MB. Your resume stays private to your account.</p>
        </>
      )}
    </div>
  );
}

function SampleReport() {
  const cats: [string, number, number][] = [["Parseability", 18, 20], ["Structure", 22, 25], ["Formatting", 15, 20], ["Contact & title", 15, 15], ["Content", 12, 20]];
  return (
    <div className="sample-report" aria-label="Example of an ATS report">
      <span className="tag">Sample report</span>
      <div className="row" style={{ gap: 18 }}>
        <ScoreRing score={82} size={104} label="Good" />
        <div className="stack" style={{ flex: 1, gap: 8 }}>
          {cats.map(([n, v, m]) => <div key={n}><div className="row between"><small>{n}</small><small>{v}/{m}</small></div><Bar value={v} max={m} /></div>)}
        </div>
      </div>
      <div className="mini-issue warn"><b>Add numbers to your bullets</b><span>Only 3 of 11 bullets show a measurable result.</span></div>
      <div className="mini-issue ok"><b>Contact details found</b><span>Email, phone and LinkedIn are easy to read.</span></div>
    </div>
  );
}

const FAQ: [string, string][] = [
  ["What is an ATS?", "An applicant tracking system is software employers use to collect and filter applications. It reads the text of your resume, sorts it into fields like experience and skills, and lets recruiters search it. If the system cannot read your resume, a recruiter may never see it."],
  ["How is my score calculated?", "Your resume is scored out of 100 across five areas: whether the text can be read cleanly (20), standard sections (25), formatting such as length, bullets and dates (20), contact details and a clear title (15), and content quality like action verbs and measurable results (20)."],
  ["What is a good ATS score?", "85 or above is excellent, 70 to 84 is good, and 50 to 69 needs some work. Below 50 usually means the file is hard for software to read, so fix the parseability issues first."],
  ["Which file format should I upload?", "A text-based PDF or a DOCX file both work well. Avoid scanned images of a resume, because there is no text for an ATS to read. If your score for parseability is low, export a fresh PDF from Word or Google Docs."],
  ["Do I need an account?", "You can explore everything without one. To scan a resume we ask you to create a free account so your report, versions and job matches are saved and only you can see them."],
  ["Will AI change my resume without asking?", "No. The optimizer only suggests rewrites. You choose which ones to keep, and it never makes up numbers: where a figure would help, it leaves a placeholder for you to fill in. Every change is saved as a new version you can undo."],
  ["Can I check my resume against a specific job?", "Yes. Paste the job description in Job Match and you get a match percentage, the skills you have, the ones that are partly covered, and the ones that are missing, with tips to close the gap."],
  ["Is my data private?", "Your files and results are tied to your account and are not visible to other users. You can delete a resume and all its versions at any time."],
];

export default function HomePage() {
  const { user } = useAuth();
  return (
    <>
      <section className="hero">
        <div className="container hero-grid">
          <div className="hero-copy">
            <nav className="crumbs" aria-label="Breadcrumb"><Link to="/">Home</Link> <span>›</span> ATS Resume Checker</nav>
            <span className="eyebrow">Free ATS resume checker</span>
            <h1>Will your resume make it past the <span className="hl">hiring software?</span></h1>
            <p className="lead">Upload your resume and get a score out of 100 in seconds, with clear, specific fixes that help it get read by applicant tracking systems and recruiters.</p>
            <UploadCard />
            {user && <p className="fine">Welcome back, {user.fullName.split(" ")[0]}. <Link to="/resumes">Open your saved resumes →</Link></p>}
          </div>
          <div className="hero-art"><SampleReport /></div>
        </div>
      </section>

      <section className="stats-band">
        <div className="container stats">
          <div><b>100</b><span>point score</span></div>
          <div><b>5</b><span>scoring areas</span></div>
          <div><b>25+</b><span>individual checks</span></div>
          <div><b>3</b><span>ATS-safe PDF templates</span></div>
        </div>
      </section>

      <section className="section">
        <div className="container">
          <div className="section-head">
            <h2>Is your resume ready for ATS?</h2>
            <p>Most large employers screen applications with software first. A strong resume can still be filtered out if the software cannot read it.</p>
          </div>
          <div className="grid three">
            <div className="feature"><div className="f-ico">🔑</div><h3>Use the right keywords</h3><p>See which skills from a job post your resume already shows, which are only partly covered, and which are missing.</p></div>
            <div className="feature"><div className="f-ico">📈</div><h3>Get specific improvements</h3><p>Every issue comes with a suggestion, ranked by how much it matters, so you know what to fix first.</p></div>
            <div className="feature"><div className="f-ico">🧾</div><h3>Fix formatting that blocks scans</h3><p>Catch unreadable text, missing sections, odd symbols and date formats before a recruiter's software does.</p></div>
          </div>
        </div>
      </section>

      <section className="section alt" id="coverage">
        <div className="container">
          <div className="section-head"><h2>What the checker looks at</h2><p>Five areas, the same ones recruiters' software relies on.</p></div>
          <div className="coverage">
            {[
              ["Parseability", "20 pts", ["Text can be extracted", "No garbled characters", "Clean line structure"]],
              ["Structure", "25 pts", ["Experience section", "Education and skills", "Summary and extras"]],
              ["Formatting", "20 pts", ["Length and word count", "Bullet points", "Consistent dates"]],
              ["Contact & title", "15 pts", ["Name, email, phone", "LinkedIn profile", "A clear job title"]],
              ["Content", "20 pts", ["Strong action verbs", "Measurable results", "No weak openers"]],
            ].map(([name, pts, items]) => (
              <div key={name as string} className="cov">
                <div className="row between"><h3>{name as string}</h3><span className="badge blue">{pts as string}</span></div>
                <ul className="checks">{(items as string[]).map((i) => <li key={i}>{i}</li>)}</ul>
              </div>
            ))}
          </div>
        </div>
      </section>

      <section className="section" id="how-it-works">
        <div className="container">
          <div className="section-head"><h2>How it works</h2><p>From upload to a stronger resume in four steps.</p></div>
          <ol className="steps">
            <li><span>1</span><h3>Upload your resume</h3><p>Drop in a PDF or DOCX. We read it the way an ATS would.</p></li>
            <li><span>2</span><h3>Review your report</h3><p>See your score, each area's points and every issue with a fix.</p></li>
            <li><span>3</span><h3>Improve it</h3><p>Edit the text, or let AI strengthen weak bullets. Each change is a new version.</p></li>
            <li><span>4</span><h3>Download & apply</h3><p>Export an ATS-safe PDF, match it to a job and track the application.</p></li>
          </ol>
        </div>
      </section>

      <section className="section alt">
        <div className="container">
          <div className="section-head"><h2>More than a checker</h2><p>Everything you need for the rest of your job search, in one place.</p></div>
          <div className="tools-grid">
            {TOOLS.map((t) => (
              <Link key={t.label} to={t.to} className="tool-card"><h3>{t.label}</h3><p>{t.desc}</p><span className="more">Explore →</span></Link>
            ))}
          </div>
        </div>
      </section>

      <section className="cta-band">
        <div className="container cta-inner">
          <div><h2>Find out where your resume stands</h2><p>It's free and takes less than a minute.</p></div>
          <button className="btn lg light" onClick={() => document.getElementById("upload")?.scrollIntoView({ behavior: "smooth", block: "center" })}>Check my score</button>
        </div>
      </section>

      <section className="section" id="faq">
        <div className="container narrow">
          <div className="section-head"><h2>Frequently asked questions</h2></div>
          <div className="faq">
            {FAQ.map(([q, a], i) => <details key={q} open={i === 0}><summary>{q}</summary><p>{a}</p></details>)}
          </div>
        </div>
      </section>
    </>
  );
}

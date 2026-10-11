import { useEffect, useState, type ReactNode } from "react";
import { ApiError } from "../api/client";

export function Spinner({ full }: { full?: boolean }) {
  return <div className={full ? "spinner-full" : "spinner-inline"}><div className="spinner" /></div>;
}

export function ErrorBox({ error }: { error: unknown }) {
  if (!error) return null;
  const msg = error instanceof ApiError ? error.message : error instanceof Error ? error.message : String(error);
  const fields = error instanceof ApiError ? Object.entries(error.fieldErrors) : [];
  return (
    <div className="alert error" role="alert">
      {msg}
      {fields.length > 0 && <ul>{fields.map(([f, m]) => <li key={f}><b>{f}</b>: {m}</li>)}</ul>}
    </div>
  );
}

export function PageHeader({ title, subtitle, actions }: { title: string; subtitle?: string; actions?: ReactNode }) {
  return (
    <div className="page-header">
      <div><h1>{title}</h1>{subtitle && <p className="muted">{subtitle}</p>}</div>
      <div className="actions">{actions}</div>
    </div>
  );
}

export function Card({ title, children, actions, className }: { title?: ReactNode; children: ReactNode; actions?: ReactNode; className?: string }) {
  return (
    <section className={`card ${className ?? ""}`}>
      {(title || actions) && <header><h3>{title}</h3><div className="actions">{actions}</div></header>}
      {children}
    </section>
  );
}

export function Empty({ children }: { children: ReactNode }) {
  return <div className="empty">{children}</div>;
}

export function Badge({ tone = "gray", children }: { tone?: "gray" | "green" | "amber" | "red" | "blue"; children: ReactNode }) {
  return <span className={`badge ${tone}`}>{children}</span>;
}

export function scoreTone(score: number | null | undefined): "green" | "amber" | "red" | "gray" {
  if (score == null) return "gray";
  return score >= 75 ? "green" : score >= 50 ? "amber" : "red";
}

export function ScoreRing({ score, label, size = 96 }: { score: number | null | undefined; label?: string; size?: number }) {
  const v = Math.max(0, Math.min(100, Math.round(score ?? 0)));
  const r = size / 2 - 8, c = 2 * Math.PI * r;
  return (
    <div className={`ring ${scoreTone(score)}`} style={{ width: size }}>
      <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`}>
        <circle cx={size / 2} cy={size / 2} r={r} className="track" />
        <circle cx={size / 2} cy={size / 2} r={r} className="value" strokeDasharray={`${(c * v) / 100} ${c}`}
          transform={`rotate(-90 ${size / 2} ${size / 2})`} />
        <text x="50%" y="50%" dominantBaseline="central" textAnchor="middle">{score == null ? "–" : v}</text>
      </svg>
      {label && <small>{label}</small>}
    </div>
  );
}

export function Bar({ value, max = 100 }: { value: number; max?: number }) {
  const pct = max === 0 ? 0 : Math.round((value / max) * 100);
  return <div className="bar"><div className={`fill ${scoreTone(pct)}`} style={{ width: `${pct}%` }} /></div>;
}

export function fmtDate(iso: string | null | undefined): string {
  if (!iso) return "–";
  return new Date(iso).toLocaleDateString(undefined, { year: "numeric", month: "short", day: "numeric" });
}

export function titleCase(s: string): string {
  return s.toLowerCase().replace(/_/g, " ").replace(/^\w/, (c) => c.toUpperCase());
}

/** Small async-data hook: runs `load`, tracks loading/error, and exposes reload(). */
export function useAsync<T>(load: () => Promise<T>, deps: unknown[]) {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [loading, setLoading] = useState(true);
  const [tick, setTick] = useState(0);
  useEffect(() => {
    let live = true;
    setLoading(true);
    load().then((d) => { if (live) { setData(d); setError(null); } })
      .catch((e) => { if (live) setError(e); })
      .finally(() => { if (live) setLoading(false); });
    return () => { live = false; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, tick]);
  return { data, error, loading, reload: () => setTick((t) => t + 1), setData };
}

export function useAction() {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  async function run<T>(fn: () => Promise<T>): Promise<T | undefined> {
    setBusy(true); setError(null);
    try { return await fn(); } catch (e) { setError(e); return undefined; } finally { setBusy(false); }
  }
  return { busy, error, run, setError };
}

export { ApiError };

import { useEffect, useState, type FormEvent } from "react";
import { ErrorBox, useAction } from "../components/ui";
import type { AuthMode } from "./AuthContext";
import { useAuth } from "./AuthContext";

interface Props { mode: AuthMode; reason?: string; onSwitch(m: AuthMode): void; onClose(): void; onDone(): void }

export default function AuthModal({ mode, reason, onSwitch, onClose, onDone }: Props) {
  const { login, register } = useAuth();
  const [fullName, setFullName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [show, setShow] = useState(false);
  const { busy, error, run, setError } = useAction();
  const signup = mode === "signup";

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === "Escape") onClose(); };
    window.addEventListener("keydown", onKey);
    document.body.style.overflow = "hidden";
    return () => { window.removeEventListener("keydown", onKey); document.body.style.overflow = ""; };
  }, [onClose]);
  useEffect(() => setError(null), [mode]); // eslint-disable-line react-hooks/exhaustive-deps

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const ok = await run(async () => {
      if (signup) await register(fullName.trim(), email.trim(), password);
      else await login(email.trim(), password);
      return true;
    });
    if (ok) onDone();
  };

  return (
    <div className="modal-bg auth-bg" onMouseDown={onClose}>
      <div className="modal auth-modal" role="dialog" aria-modal="true" aria-labelledby="auth-title" onMouseDown={(e) => e.stopPropagation()}>
        <button className="icon-btn modal-close" onClick={onClose} aria-label="Close">✕</button>
        <div className="auth-side">
          <div className="logo-mark big">S</div>
          <h3>Your job search, organised</h3>
          <ul className="checks">
            <li>Instant ATS score and fixes</li>
            <li>Match your resume to any job</li>
            <li>AI bullet rewrites and cover letters</li>
            <li>Track every application in one place</li>
          </ul>
        </div>
        <form className="auth-form" onSubmit={submit}>
          <h2 id="auth-title">{signup ? "Create your free account" : "Welcome back"}</h2>
          <p className="muted">{reason ?? (signup ? "It takes less than a minute." : "Sign in to continue.")}</p>
          <div className="seg">
            <button type="button" className={!signup ? "on" : ""} onClick={() => onSwitch("signin")}>Sign in</button>
            <button type="button" className={signup ? "on" : ""} onClick={() => onSwitch("signup")}>Sign up</button>
          </div>
          <ErrorBox error={error} />
          {signup && (
            <div className="field"><label htmlFor="a-name">Full name</label>
              <input id="a-name" value={fullName} onChange={(e) => setFullName(e.target.value)} required autoComplete="name" autoFocus /></div>
          )}
          <div className="field"><label htmlFor="a-email">Email</label>
            <input id="a-email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} required autoComplete="email" autoFocus={!signup} /></div>
          <div className="field"><label htmlFor="a-pass">Password</label>
            <div className="pw-wrap">
              <input id="a-pass" type={show ? "text" : "password"} value={password} onChange={(e) => setPassword(e.target.value)} required
                minLength={signup ? 8 : undefined} autoComplete={signup ? "new-password" : "current-password"} />
              <button type="button" className="pw-toggle" onClick={() => setShow(!show)}>{show ? "Hide" : "Show"}</button>
            </div>
            {signup && <div className="hint">8 to 72 characters, with at least one letter and one digit.</div>}
          </div>
          <button className="btn block lg" disabled={busy}>{busy ? "Please wait…" : signup ? "Create account" : "Sign in"}</button>
          <p className="muted small center">
            {signup ? <>Already have an account? <a href="#" onClick={(e) => { e.preventDefault(); onSwitch("signin"); }}>Sign in</a></>
              : <>New here? <a href="#" onClick={(e) => { e.preventDefault(); onSwitch("signup"); }}>Create a free account</a></>}
          </p>
        </form>
      </div>
    </div>
  );
}

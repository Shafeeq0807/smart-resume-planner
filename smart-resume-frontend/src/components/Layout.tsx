import { useEffect, useRef, useState, type ReactNode } from "react";
import { Link, NavLink, useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { ThemeToggle } from "../theme/ThemeContext";

export const TOOLS = [
  { to: "/job-match", label: "Job Match", desc: "See how well your resume fits a job" },
  { to: "/optimizer", label: "Bullet Optimizer", desc: "Turn duties into achievements with AI" },
  { to: "/job-match", label: "Cover Letters & Interview Prep", desc: "Generated from your resume and the job" },
  { to: "/tracker", label: "Application Tracker", desc: "A board for every application" },
  { to: "/coach", label: "AI Career Coach", desc: "Ask anything about your search" },
  { to: "/github", label: "GitHub Projects", desc: "Turn repos into resume bullets" },
] as const;

export function Logo() {
  return (
    <Link to="/" className="logo" aria-label="Smart Resume Planner home">
      <span className="logo-mark">S</span>
      <span className="logo-text">Smart<b>Resume</b></span>
    </Link>
  );
}

function initials(name?: string) {
  return (name ?? "?").split(/\s+/).filter(Boolean).slice(0, 2).map((p) => p[0]!.toUpperCase()).join("");
}

export default function Layout({ children }: { children: ReactNode }) {
  const { user, logout, openAuth } = useAuth();
  const nav = useNavigate();
  const loc = useLocation();
  const [menu, setMenu] = useState<"tools" | "user" | "mobile" | null>(null);
  const headerRef = useRef<HTMLElement>(null);
  const isHome = loc.pathname === "/";

  useEffect(() => { setMenu(null); window.scrollTo(0, 0); }, [loc.pathname]);
  useEffect(() => {
    const close = (e: MouseEvent) => { if (!headerRef.current?.contains(e.target as Node)) setMenu(null); };
    document.addEventListener("mousedown", close);
    return () => document.removeEventListener("mousedown", close);
  }, []);
  const toggle = (m: "tools" | "user" | "mobile") => setMenu((cur) => (cur === m ? null : m));

  return (
    <div className="site">
      <header className="site-header" ref={headerRef}>
        <div className="container header-inner">
          <Logo />
          <nav className={`main-nav ${menu === "mobile" ? "open" : ""}`} aria-label="Main">
            <NavLink to="/" end>ATS Checker</NavLink>
            <div className="dropdown">
              <button className={`nav-btn ${menu === "tools" ? "on" : ""}`} onClick={() => toggle("tools")} aria-expanded={menu === "tools"}>
                Tools <span className="caret">▾</span>
              </button>
              {menu === "tools" && (
                <div className="dropdown-panel tools-panel">
                  {TOOLS.map((t) => (
                    <Link key={t.label} to={t.to} className="tool-link"><b>{t.label}</b><small>{t.desc}</small></Link>
                  ))}
                </div>
              )}
            </div>
            <a href="/#how-it-works" onClick={(e) => { if (isHome) { e.preventDefault(); document.getElementById("how-it-works")?.scrollIntoView({ behavior: "smooth" }); } }}>How it works</a>
            <a href="/#faq" onClick={(e) => { if (isHome) { e.preventDefault(); document.getElementById("faq")?.scrollIntoView({ behavior: "smooth" }); } }}>FAQ</a>
            {user && <NavLink to="/resumes">My Resumes</NavLink>}
            {!user && <a href="#" className="show-sm" onClick={(e) => { e.preventDefault(); setMenu(null); openAuth("signin"); }}>Log in</a>}
          </nav>
          <div className="header-actions">
            <ThemeToggle />
            {user ? (
              <div className="dropdown">
                <button className="avatar" onClick={() => toggle("user")} aria-label="Account menu" aria-expanded={menu === "user"}>{initials(user.fullName)}</button>
                {menu === "user" && (
                  <div className="dropdown-panel right user-panel">
                    <div className="user-head"><b>{user.fullName}</b><small>{user.email}</small></div>
                    <Link to="/dashboard">Dashboard</Link>
                    <Link to="/resumes">My resumes</Link>
                    <Link to="/tracker">Application tracker</Link>
                    <Link to="/profile">Profile & password</Link>
                    <button onClick={async () => { await logout(); nav("/"); }}>Sign out</button>
                  </div>
                )}
              </div>
            ) : (
              <>
                <button className="btn ghost hide-sm" onClick={() => openAuth("signin")}>Log in</button>
                <button className="btn" onClick={() => openAuth("signup")}><span className="lbl-long">Get started free</span><span className="lbl-short">Sign up</span></button>
              </>
            )}
            <button className="icon-btn burger" onClick={() => toggle("mobile")} aria-label="Menu" aria-expanded={menu === "mobile"}>☰</button>
          </div>
        </div>
      </header>

      <main className={isHome ? "site-main" : "site-main container page"}>{children}</main>

      <footer className="site-footer">
        <div className="container footer-grid">
          <div className="footer-brand">
            <Logo />
            <p>Check your resume against applicant tracking systems, tailor it to every job, and keep your search organised.</p>
            <Link className="btn" to="/">Check my resume</Link>
          </div>
          <div>
            <h4>Tools</h4>
            <Link to="/">ATS Resume Checker</Link>
            {TOOLS.filter((t, i, a) => a.findIndex((x) => x.to === t.to) === i).map((t) => <Link key={t.to} to={t.to}>{t.label}</Link>)}
          </div>
          <div>
            <h4>Resources</h4>
            <a href="/#how-it-works">How it works</a>
            <a href="/#coverage">What we check</a>
            <a href="/#faq">FAQ</a>
          </div>
          <div>
            <h4>Account</h4>
            {user ? (<>
              <Link to="/dashboard">Dashboard</Link>
              <Link to="/resumes">My resumes</Link>
              <Link to="/profile">Profile</Link>
            </>) : (<>
              <a href="#" onClick={(e) => { e.preventDefault(); openAuth("signin"); }}>Log in</a>
              <a href="#" onClick={(e) => { e.preventDefault(); openAuth("signup"); }}>Create free account</a>
            </>)}
          </div>
        </div>
        <div className="container footer-bottom">
          <span>© {new Date().getFullYear()} Smart Resume Planner</span>
          <span className="row" style={{ gap: 8 }}>Theme <ThemeToggle /></span>
        </div>
      </footer>
    </div>
  );
}

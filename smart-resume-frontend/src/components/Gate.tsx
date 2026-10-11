import type { ReactNode } from "react";
import { useAuth } from "../auth/AuthContext";

interface Props {
  children: ReactNode;
  eyebrow: string;
  title: string;
  text: string;
  points: string[];
  cta: string;
}

/** Shows the tool to signed-in users; everyone else sees a short landing for it, and the button opens the sign-up pop-up. */
export default function Gate({ children, eyebrow, title, text, points, cta }: Props) {
  const { user, openAuth } = useAuth();
  if (user) return <>{children}</>;
  return (
    <section className="gate">
      <div className="gate-copy">
        <span className="eyebrow">{eyebrow}</span>
        <h1>{title}</h1>
        <p className="lead">{text}</p>
        <ul className="checks">{points.map((p) => <li key={p}>{p}</li>)}</ul>
        <div className="row">
          <button className="btn lg" onClick={() => openAuth("signup", `Create a free account to use ${eyebrow}.`)}>{cta}</button>
          <button className="btn ghost lg" onClick={() => openAuth("signin")}>I have an account</button>
        </div>
      </div>
      <div className="gate-art" aria-hidden="true">
        <div className="art-card a1"><span className="dot g" /><span className="line w70" /><span className="line w40" /></div>
        <div className="art-card a2"><span className="dot b" /><span className="line w60" /><span className="line w80" /><span className="line w30" /></div>
        <div className="art-card a3"><span className="dot y" /><span className="line w50" /><span className="line w70" /></div>
      </div>
    </section>
  );
}

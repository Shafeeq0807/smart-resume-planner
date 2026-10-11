import { useState, type FormEvent } from "react";
import { api } from "../api/client";
import type { User } from "../api/types";
import { useAuth } from "../auth/AuthContext";
import { Card, ErrorBox, PageHeader, useAction } from "../components/ui";

export default function ProfilePage() {
  const { user, setUser, logout } = useAuth();
  const [fullName, setFullName] = useState(user?.fullName ?? "");
  const [headline, setHeadline] = useState(user?.headline ?? "");
  const [github, setGithub] = useState(user?.githubUsername ?? "");
  const [current, setCurrent] = useState("");
  const [next, setNext] = useState("");
  const profile = useAction();
  const pw = useAction();
  const [saved, setSaved] = useState<string | null>(null);

  const saveProfile = async (e: FormEvent) => {
    e.preventDefault(); setSaved(null);
    const u = await profile.run(() => api.patch<User>("/api/users/me", { fullName, headline, githubUsername: github }));
    if (u) { setUser(u); setSaved("Profile saved."); }
  };
  const changePw = async (e: FormEvent) => {
    e.preventDefault(); setSaved(null);
    const ok = await pw.run(async () => { await api.post("/api/users/me/password", { currentPassword: current, newPassword: next }); return true; });
    if (ok) { setCurrent(""); setNext(""); setSaved("Password changed. Please sign in again."); await logout(); }
  };

  return (
    <>
      <PageHeader title="Profile" subtitle={user?.email} />
      {saved && <div className="alert ok">{saved}</div>}
      <div className="grid two">
        <Card title="Your details">
          <form onSubmit={saveProfile}>
            <ErrorBox error={profile.error} />
            <div className="field"><label>Full name</label><input value={fullName} onChange={(e) => setFullName(e.target.value)} required /></div>
            <div className="field"><label>Headline</label><input value={headline} onChange={(e) => setHeadline(e.target.value)} placeholder="Backend engineer, Java & Spring" /></div>
            <div className="field"><label>GitHub username</label><input value={github} onChange={(e) => setGithub(e.target.value)} /></div>
            <button className="btn" disabled={profile.busy}>Save</button>
          </form>
        </Card>
        <Card title="Change password">
          <form onSubmit={changePw}>
            <ErrorBox error={pw.error} />
            <div className="field"><label>Current password</label><input type="password" value={current} onChange={(e) => setCurrent(e.target.value)} required /></div>
            <div className="field"><label>New password</label><input type="password" value={next} onChange={(e) => setNext(e.target.value)} required minLength={8} /></div>
            <button className="btn" disabled={pw.busy}>Change password</button>
          </form>
        </Card>
      </div>
    </>
  );
}

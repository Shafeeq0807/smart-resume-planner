import { useEffect, useRef, useState, type FormEvent } from "react";
import { api, streamChat } from "../api/client";
import type { ChatMessage, ChatSession, ChatSessionDetail, JobDescriptionSummary, Paged, ResumeSummary } from "../api/types";
import { Card, Empty, ErrorBox, PageHeader, Spinner, useAction, useAsync } from "../components/ui";

export default function ChatPage() {
  const sessions = useAsync(() => api.get<Paged<ChatSession>>("/api/chat/sessions?size=50"), []);
  const resumes = useAsync(() => api.get<Paged<ResumeSummary>>("/api/resumes?size=50"), []);
  const jobs = useAsync(() => api.get<Paged<JobDescriptionSummary>>("/api/job-descriptions?size=50"), []);
  const [activeId, setActiveId] = useState<number | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [draft, setDraft] = useState("");
  const [streaming, setStreaming] = useState<string | null>(null);
  const [resumeId, setResumeId] = useState<number | "">("");
  const [jobId, setJobId] = useState<number | "">("");
  const act = useAction();
  const bottom = useRef<HTMLDivElement>(null);

  useEffect(() => { bottom.current?.scrollIntoView({ behavior: "smooth" }); }, [messages, streaming]);

  const open = async (id: number) => {
    setActiveId(id); setStreaming(null);
    const d = await act.run(() => api.get<ChatSessionDetail>(`/api/chat/sessions/${id}`));
    if (d) setMessages(d.messages);
  };
  const create = async () => {
    const s = await act.run(() => api.post<ChatSession>("/api/chat/sessions", {
      resumeId: resumeId === "" ? null : resumeId, jobDescriptionId: jobId === "" ? null : jobId, title: "New chat",
    }));
    if (s) { sessions.reload(); setMessages([]); setActiveId(s.id); }
  };
  const remove = async (id: number) => {
    if (!window.confirm("Delete this conversation?")) return;
    if (await act.run(async () => { await api.del(`/api/chat/sessions/${id}`); return true; })) { if (activeId === id) { setActiveId(null); setMessages([]); } sessions.reload(); }
  };

  const send = async (e: FormEvent) => {
    e.preventDefault();
    const text = draft.trim();
    if (!text || activeId == null || streaming !== null) return;
    setDraft("");
    setMessages((m) => [...m, { id: -Date.now(), role: "USER", content: text, createdAt: new Date().toISOString() }]);
    setStreaming("");
    let acc = "";
    const ok = await act.run(async () => { await streamChat(activeId, text, (t) => { acc += t; setStreaming(acc); }); return true; });
    setStreaming(null);
    if (ok || acc) await open(activeId); // reload so saved message ids and any auto title are in sync
    sessions.reload();
  };

  return (
    <>
      <PageHeader title="AI Career Coach" subtitle="Ask about your resume, a job, or interview prep. Attach a resume and job for specific answers." />
      <div className="chat">
        <Card className="chat-list" title="Conversations">
          <div className="stack" style={{ marginBottom: 12 }}>
            <select value={resumeId} onChange={(e) => setResumeId(e.target.value === "" ? "" : Number(e.target.value))}>
              <option value="">No resume attached</option>{resumes.data?.items.map((r) => <option key={r.id} value={r.id}>{r.title}</option>)}</select>
            <select value={jobId} onChange={(e) => setJobId(e.target.value === "" ? "" : Number(e.target.value))}>
              <option value="">No job attached</option>{jobs.data?.items.map((j) => <option key={j.id} value={j.id}>{j.title}</option>)}</select>
            <button className="btn" onClick={create} disabled={act.busy}>New chat</button>
          </div>
          {sessions.loading ? <Spinner /> : sessions.data?.items.map((s) => (
            <div key={s.id} className={`item ${s.id === activeId ? "active" : ""}`} onClick={() => open(s.id)}>
              <span>{s.title}</span><span title="Delete" onClick={(e) => { e.stopPropagation(); void remove(s.id); }}>✕</span>
            </div>
          ))}
        </Card>
        <Card className="chat-pane">
          <ErrorBox error={act.error} />
          {activeId == null ? <Empty>Pick a conversation or start a new one.</Empty> : (
            <>
              <div className="msgs">
                {messages.length === 0 && streaming === null && <Empty>Say hello, or ask “How can I make my resume stronger for this job?”</Empty>}
                {messages.map((m) => <div key={m.id} className={`msg ${m.role}`}>{m.content}</div>)}
                {streaming !== null && <div className="msg ASSISTANT">{streaming || "…"}</div>}
                <div ref={bottom} />
              </div>
              <form className="row" onSubmit={send}>
                <input style={{ flex: 1 }} value={draft} onChange={(e) => setDraft(e.target.value)} placeholder="Type your question…" maxLength={4000} disabled={streaming !== null} />
                <button className="btn" disabled={streaming !== null || !draft.trim()}>Send</button>
              </form>
            </>
          )}
        </Card>
      </div>
    </>
  );
}

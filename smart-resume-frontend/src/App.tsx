import { useEffect, type ReactNode } from "react";
import { Navigate, Route, Routes } from "react-router-dom";
import { useAuth, type AuthMode } from "./auth/AuthContext";
import Gate from "./components/Gate";
import Layout from "./components/Layout";
import { Spinner } from "./components/ui";
import ChatPage from "./pages/ChatPage";
import Dashboard from "./pages/Dashboard";
import GithubPage from "./pages/GithubPage";
import HomePage from "./pages/HomePage";
import JobDetailPage from "./pages/JobDetailPage";
import JobsPage from "./pages/JobsPage";
import MatchPage from "./pages/MatchPage";
import OptimizerPage from "./pages/OptimizerPage";
import ProfilePage from "./pages/ProfilePage";
import ResumeDetailPage from "./pages/ResumeDetailPage";
import ResumesPage from "./pages/ResumesPage";
import TrackerPage from "./pages/TrackerPage";

/** /login and /register links open the pop-up over the home page. */
function OpenAuth({ mode }: { mode: AuthMode }) {
  const { user, openAuth } = useAuth();
  useEffect(() => { if (!user) openAuth(mode); }, []); // eslint-disable-line react-hooks/exhaustive-deps
  return <Navigate to={user ? "/dashboard" : "/"} replace />;
}

const gates: Record<string, Omit<Parameters<typeof Gate>[0], "children">> = {
  account: { eyebrow: "Your dashboard", title: "Everything about your job search in one place", text: "See your resumes, recent job matches, application stats and follow-ups at a glance.", points: ["ATS scores for every resume", "Recent matches and follow-ups", "Interview and offer rates"], cta: "Create free account" },
  resumes: { eyebrow: "My resumes", title: "Keep every version of your resume", text: "Each upload and edit is saved as a version with its own ATS score, so you can compare them side by side or go back any time.", points: ["Version history with restore", "Side-by-side compare", "ATS-safe PDF export in 3 templates"], cta: "Create free account" },
  optimizer: { eyebrow: "the Bullet Optimizer", title: "Turn duties into achievements", text: "AI finds your weakest bullet points and rewrites them as results-focused statements. You approve every change.", points: ["Finds weak bullets automatically", "Never invents numbers: you fill in the real ones", "Tailor rewrites to a specific job"], cta: "Try the optimizer" },
  tracker: { eyebrow: "the Application Tracker", title: "Never lose track of an application", text: "A simple board for every job you're chasing, from saved to offer, with follow-up reminders.", points: ["Drag-and-drop board", "Follow-up dates on your dashboard", "Interview and offer rates"], cta: "Start tracking" },
  coach: { eyebrow: "the AI Career Coach", title: "Ask a career coach, any time", text: "Chat about your resume, a specific job or interview prep. Attach a resume and a job for answers about your situation.", points: ["Answers stream in as they're written", "Knows your resume and the job", "Conversations are saved"], cta: "Start a conversation" },
  github: { eyebrow: "GitHub Projects", title: "Show what you've built", text: "We analyse your public repositories, detect your tech stack and write resume bullets for your best projects.", points: ["Skills detected from your code", "README and project quality scores", "Add projects to your resume in one click"], cta: "Analyse my GitHub" },
};

const gated = (key: keyof typeof gates, el: ReactNode) => <Gate {...gates[key]}>{el}</Gate>;

export default function App() {
  const { loading } = useAuth();
  if (loading) return <Spinner full />;
  return (
    <Layout>
      <Routes>
        <Route path="/" element={<HomePage />} />
        <Route path="/login" element={<OpenAuth mode="signin" />} />
        <Route path="/register" element={<OpenAuth mode="signup" />} />
        <Route path="/dashboard" element={gated("account", <Dashboard />)} />
        <Route path="/resumes" element={gated("resumes", <ResumesPage />)} />
        <Route path="/resumes/:id" element={gated("resumes", <ResumeDetailPage />)} />
        <Route path="/job-match" element={<JobsPage />} />
        <Route path="/jobs" element={<JobsPage />} />
        <Route path="/jobs/:id" element={gated("resumes", <JobDetailPage />)} />
        <Route path="/matches/:id" element={gated("resumes", <MatchPage />)} />
        <Route path="/optimizer" element={gated("optimizer", <OptimizerPage />)} />
        <Route path="/tracker" element={gated("tracker", <TrackerPage />)} />
        <Route path="/coach" element={gated("coach", <ChatPage />)} />
        <Route path="/chat" element={<Navigate to="/coach" replace />} />
        <Route path="/github" element={gated("github", <GithubPage />)} />
        <Route path="/profile" element={gated("account", <ProfilePage />)} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Layout>
  );
}

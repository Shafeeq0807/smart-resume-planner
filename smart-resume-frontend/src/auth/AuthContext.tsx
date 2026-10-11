import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { api, setSessionExpiredHandler, tokens } from "../api/client";
import type { AuthResponse, User } from "../api/types";
import AuthModal from "./AuthModal";

export type AuthMode = "signin" | "signup";

interface AuthState {
  user: User | null;
  loading: boolean;
  login(email: string, password: string): Promise<void>;
  register(fullName: string, email: string, password: string): Promise<void>;
  logout(): Promise<void>;
  setUser(u: User): void;
  /** Opens the sign-in pop-up. */
  openAuth(mode?: AuthMode, reason?: string): void;
  /** Runs `action` now if signed in; otherwise opens the pop-up and runs it right after sign-in/sign-up. */
  requireAuth(action: () => void, reason?: string, mode?: AuthMode): void;
}

const Ctx = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);
  const [modal, setModal] = useState<{ mode: AuthMode; reason?: string } | null>(null);
  const pending = useRef<(() => void) | null>(null);

  useEffect(() => {
    setSessionExpiredHandler(() => {
      setUser(null);
      setModal({ mode: "signin", reason: "Your session has ended. Please sign in again." });
    });
    if (!tokens.access && !tokens.refresh) { setLoading(false); return; }
    api.get<User>("/api/auth/me").then(setUser).catch(() => tokens.clear()).finally(() => setLoading(false));
  }, []);

  const accept = (r: AuthResponse) => { tokens.set(r.accessToken, r.refreshToken); setUser(r.user); };

  const login = useCallback(async (email: string, password: string) => {
    accept(await api.public<AuthResponse>("POST", "/api/auth/login", { email, password }));
  }, []);
  const register = useCallback(async (fullName: string, email: string, password: string) => {
    accept(await api.public<AuthResponse>("POST", "/api/auth/register", { fullName, email, password }));
  }, []);
  const logout = useCallback(async () => {
    const refreshToken = tokens.refresh;
    if (refreshToken) { try { await api.public("POST", "/api/auth/logout", { refreshToken }); } catch { /* already invalid */ } }
    tokens.clear(); setUser(null);
  }, []);

  const openAuth = useCallback((mode: AuthMode = "signin", reason?: string) => {
    pending.current = null;
    setModal({ mode, reason });
  }, []);

  const requireAuth = useCallback((action: () => void, reason?: string, mode: AuthMode = "signup") => {
    if (tokens.access || tokens.refresh) { action(); return; }
    pending.current = action;
    setModal({ mode, reason });
  }, []);

  const onAuthenticated = () => {
    setModal(null);
    const next = pending.current;
    pending.current = null;
    // Let React commit the signed-in state before the queued action runs.
    if (next) setTimeout(next, 0);
  };

  const value = useMemo(() => ({ user, loading, login, register, logout, setUser, openAuth, requireAuth }),
    [user, loading, login, register, logout, openAuth, requireAuth]);

  return (
    <Ctx.Provider value={value}>
      {children}
      {modal && (
        <AuthModal mode={modal.mode} reason={modal.reason} onSwitch={(mode) => setModal({ ...modal, mode })}
          onClose={() => { pending.current = null; setModal(null); }} onDone={onAuthenticated} />
      )}
    </Ctx.Provider>
  );
}

export function useAuth(): AuthState {
  const v = useContext(Ctx);
  if (!v) throw new Error("useAuth outside AuthProvider");
  return v;
}

"""Fake OpenAI-compatible chat API + fake GitHub REST API for local end-to-end tests."""
import json, re, sys, time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

CALLS = []

def completion(system, user):
    s = " ".join(system.lower().split())
    if "extract skill requirements" in s:
        return json.dumps({
            "hardSkills": [{"name": "Java", "importance": "REQUIRED"}, {"name": "Spring Boot", "importance": "REQUIRED"},
                           {"name": "PostgreSQL", "importance": "REQUIRED"}, {"name": "REST APIs", "importance": "REQUIRED"},
                           {"name": "GraphQL", "importance": "PREFERRED"}],
            "softSkills": [{"name": "Communication", "importance": "REQUIRED"}, {"name": "Leadership", "importance": "PREFERRED"}],
            "tools": [{"name": "Docker", "importance": "REQUIRED"}, {"name": "Terraform", "importance": "PREFERRED"}],
            "certifications": [], "seniority": "SENIOR"})
    if "rewrite weak resume bullet" in s:
        items = []
        for m in re.finditer(r"^(\d+) \[([^\]]*)\]: (.*)$", user, re.M):
            idx, _, text = int(m.group(1)), m.group(2), m.group(3)
            has_num = bool(re.search(r"\d", text))
            rewritten = ("Delivered " + text.rstrip(".").lower() + " as measured by [X%] improvement, by applying best practices")
            items.append({"index": idx, "rewritten": rewritten, "needsMetric": not has_num})
        return json.dumps({"rewrites": items})
    if "career coach" in s:
        return json.dumps({"recommendations": ["Add PostgreSQL evidence if you have used it.", "Mention GraphQL if you know it.", "Quantify your payment-service work."]})
    if "cover letters" in s:
        return "Dear Hiring Team,\n\nI am excited to apply for this role. At Acme I reduced API latency by 38% with Redis caching.\n\nSincerely,\nPriya Sharma"
    if "prepare candidates for interviews" in s:
        n = int(re.search(r"exactly (\d+) questions", s).group(1))
        return json.dumps({"questions": [{"question": f"Question {i+1}: how did you reduce latency?", "category": "TECHNICAL",
                                          "whatInterviewersLookFor": "Concrete approach", "answerOutline": "Talk about Redis caching at Acme."} for i in range(n)]})
    if "github repositories into resume bullet" in s:
        repos = re.findall(r"^- ([\w.-]+):", user, re.M)
        return json.dumps({"bullets": [{"repo": r, "bullet": f"Built {r} with Java and Spring Boot to automate resume analysis"} for r in repos]})
    if "ats readiness" in s:
        return "Your resume is readable by ATS software. Add more quantified results and an explicit summary."
    return "Here is some feedback: lead with your strongest achievement and add numbers to the payment service bullet."

class H(BaseHTTPRequestHandler):
    def log_message(self, *a): pass

    def _send(self, code, body, ctype="application/json"):
        data = body if isinstance(body, bytes) else body.encode()
        self.send_response(code); self.send_header("Content-Type", ctype); self.send_header("Content-Length", str(len(data))); self.end_headers(); self.wfile.write(data)

    def _body(self):
        if "chunked" in self.headers.get("Transfer-Encoding", "").lower():
            out = b""
            while True:
                size = int(self.rfile.readline().strip() or b"0", 16)
                if size == 0:
                    self.rfile.readline(); break
                out += self.rfile.read(size); self.rfile.readline()
            return out
        return self.rfile.read(int(self.headers.get("Content-Length", 0)))

    def do_POST(self):
        req = json.loads(self._body() or b"{}")
        if self.path.endswith("/chat/completions"):
            msgs = req.get("messages", [])
            system = next((m["content"] for m in msgs if m["role"] in ("system", "developer")), "")
            user = next((m["content"] for m in reversed(msgs) if m["role"] == "user"), "")
            if isinstance(user, list): user = " ".join(p.get("text", "") for p in user)
            if isinstance(system, list): system = " ".join(p.get("text", "") for p in system)
            CALLS.append(system[:60]); open("/tmp/claude-0/req.log","a").write(json.dumps(req)[:600]+"\n")
            if FAIL_AI:
                return self._send(500, json.dumps({"error": {"message": "boom"}}))
            text = completion(system, user)
            if req.get("stream"):
                self.send_response(200); self.send_header("Content-Type", "text/event-stream"); self.end_headers()
                for w in text.split(" "):
                    chunk = {"id": "c1", "object": "chat.completion.chunk", "model": "fake", "choices": [{"index": 0, "delta": {"content": w + " "}}]}
                    self.wfile.write(f"data: {json.dumps(chunk)}\n\n".encode()); self.wfile.flush(); time.sleep(0.01)
                self.wfile.write(b"data: " + json.dumps({"id": "c1", "object": "chat.completion.chunk", "model": "fake", "choices": [{"index": 0, "delta": {}, "finish_reason": "stop"}]}).encode() + b"\n\ndata: [DONE]\n\n"); return
            return self._send(200, json.dumps({"id": "x", "object": "chat.completion", "model": "fake-model",
                "choices": [{"index": 0, "message": {"role": "assistant", "content": text}, "finish_reason": "stop"}],
                "usage": {"prompt_tokens": 100, "completion_tokens": 50, "total_tokens": 150}}))
        self._send(404, "{}")

    def do_GET(self):
        p = self.path.split("?")[0]
        if p == "/_calls": return self._send(200, json.dumps(CALLS))
        if p == "/users/ghost/repos": return self._send(404, json.dumps({"message": "Not Found"}))
        if p == "/users/priyasharma/repos":
            return self._send(200, json.dumps([
                {"name": "resume-analyzer", "html_url": "https://github.com/priyasharma/resume-analyzer", "description": "Analyzes resumes for ATS",
                 "language": "Java", "stargazers_count": 12, "forks_count": 3, "pushed_at": "2026-09-01T10:00:00Z", "fork": False, "archived": False, "topics": ["spring-boot", "mysql"], "size": 4200},
                {"name": "forked-thing", "html_url": "https://github.com/priyasharma/forked-thing", "description": "a fork", "language": "Go", "stargazers_count": 100, "forks_count": 1, "pushed_at": "2026-09-01T10:00:00Z", "fork": True, "archived": False, "topics": [], "size": 10},
                {"name": "dotfiles", "html_url": "https://github.com/priyasharma/dotfiles", "description": None, "language": "Shell", "stargazers_count": 0, "forks_count": 0, "pushed_at": "2023-01-01T10:00:00Z", "fork": False, "archived": False, "topics": [], "size": 20}]))
        m = re.match(r"/repos/[^/]+/([^/]+)/languages", p)
        if m: return self._send(200, json.dumps({"Java": 90000, "HTML": 5000} if m.group(1) == "resume-analyzer" else {"Shell": 300}))
        m = re.match(r"/repos/[^/]+/([^/]+)/readme", p)
        if m:
            if m.group(1) == "resume-analyzer":
                return self._send(200, "# Resume Analyzer\n\n## Features\n- Uses Spring Boot and Docker\n\n## Installation\n```\nmvn spring-boot:run\n```\n\nLicense MIT. " + "x" * 400, "text/plain")
            return self._send(404, "{}")
        self._send(404, "{}")

FAIL_AI = False
if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 9100
    FAIL_AI = len(sys.argv) > 2 and sys.argv[2] == "fail"
    ThreadingHTTPServer(("127.0.0.1", port), H).serve_forever()

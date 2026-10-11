import json, sys, time, uuid, requests

BASE = "http://localhost:8080"
FAILS = []

def check(name, cond, detail=""):
    print(("PASS " if cond else "FAIL ") + name + ((" :: " + str(detail)[:300]) if not cond else ""))
    if not cond:
        FAILS.append(name)

def call(method, path, token=None, expect=None, **kw):
    headers = kw.pop("headers", {})
    if token:
        headers["Authorization"] = "Bearer " + token
    r = requests.request(method, BASE + path, headers=headers, timeout=60, **kw)
    if expect is not None:
        check(f"{method} {path} -> {expect}", r.status_code == expect, f"{r.status_code} {r.text}")
    return r

email = f"priya-{uuid.uuid4().hex[:8]}@example.com"
pw = "Passw0rd123"

# ---------------- auth ----------------
r = call("POST", "/api/auth/register", json={"email": email, "password": pw, "fullName": "Priya Sharma"}, expect=201)
reg = r.json(); tok = reg["accessToken"]; refresh = reg["refreshToken"]
check("register returns tokens + user", reg["user"]["email"] == email and reg["tokenType"] == "Bearer")
call("POST", "/api/auth/register", json={"email": email, "password": pw, "fullName": "Priya"}, expect=409)
call("POST", "/api/auth/register", json={"email": "bad", "password": "short", "fullName": ""}, expect=400)
r = call("POST", "/api/auth/login", json={"email": email, "password": "wrong-pass1"}, expect=401)
check("login bad creds has code", r.json().get("code") == "BAD_CREDENTIALS", r.text)
r = call("POST", "/api/auth/login", json={"email": email.upper(), "password": pw}, expect=200)
tok = r.json()["accessToken"]; refresh = r.json()["refreshToken"]
call("GET", "/api/auth/me", expect=401)
r = call("GET", "/api/auth/me", token=tok, expect=200); check("me email", r.json()["email"] == email)
call("GET", "/api/auth/me", token="garbage", expect=401)
r = call("POST", "/api/auth/refresh", json={"refreshToken": refresh}, expect=200)
new_refresh = r.json()["refreshToken"]; tok = r.json()["accessToken"]
call("POST", "/api/auth/refresh", json={"refreshToken": refresh}, expect=401)  # reuse detected
call("POST", "/api/auth/refresh", json={"refreshToken": new_refresh}, expect=401)  # family revoked
r = call("POST", "/api/auth/login", json={"email": email, "password": pw}, expect=200)
tok = r.json()["accessToken"]; refresh = r.json()["refreshToken"]
call("PATCH", "/api/users/me", token=tok, json={"headline": "Backend dev", "githubUsername": "priyasharma"}, expect=200)
call("PATCH", "/api/users/me", token=tok, json={"githubUsername": "-bad-"}, expect=400)

# second user for IDOR tests
email2 = f"other-{uuid.uuid4().hex[:8]}@example.com"
r = call("POST", "/api/auth/register", json={"email": email2, "password": pw, "fullName": "Other User"}, expect=201)
tok2 = r.json()["accessToken"]

# ---------------- resumes ----------------
T = "/tmp/claude-0/-home-claude/90079b7c-c693-57da-80dd-2ba72c746070/scratchpad/test/"
def upload(path, token, fname=None, **data):
    with open(T + path, "rb") as f:
        return call("POST", "/api/resumes", token=token, files={"file": (fname or path, f)}, data=data)

r = upload("not_a_resume.pdf", tok); check("fake pdf rejected 415", r.status_code == 415, r.text)
r = call("POST", "/api/resumes", token=tok, files={"file": ("x.txt", b"hello world " * 20)}); check("txt rejected 415", r.status_code == 415, r.text)
r = call("POST", "/api/resumes", token=tok, files={"title": (None, "x")}); check("missing file 400", r.status_code == 400, r.text)
r = upload("resume.pdf", tok, title="Priya Resume", targetRole="Senior Java Developer"); check("upload pdf 201", r.status_code == 201, r.text)
res = r.json(); rid = res["resume"]["id"]; v1 = res["currentVersion"]
print(json.dumps({k: v1[k] for k in ("id", "versionNumber", "pageCount", "wordCount")}), "ATS", v1["ats"]["overallScore"], v1["ats"]["grade"])
check("ATS score present", 0 < v1["ats"]["overallScore"] <= 100)
check("sections detected", {"EXPERIENCE", "EDUCATION", "SKILLS"} <= set(v1["ats"]["detectedSections"]), v1["ats"]["detectedSections"])
doc = v1["structuredContent"]
check("contact parsed", doc["contact"]["email"] == "priya.sharma@example.com" and doc["contact"]["fullName"] == "Priya Sharma", doc["contact"])
check("2 experiences parsed", len(doc["experience"]) == 2, [e["title"] + "@" + e["company"] for e in doc["experience"]])
check("experience bullets parsed", len(doc["experience"][0]["bullets"]) == 4, doc["experience"][0])
check("skills parsed", "Spring Boot" in doc["skills"] and "Docker" in doc["skills"], doc["skills"])
check("education parsed", len(doc["education"]) == 1, doc["education"])
check("project parsed", len(doc["projects"]) == 1 and doc["projects"][0]["bullets"], doc["projects"])
print("   title:", v1["ats"]["detectedTitle"], "| exp0:", doc["experience"][0]["title"], "|", doc["experience"][0]["company"], "|", doc["experience"][0]["startDate"], "-", doc["experience"][0]["endDate"])
for i in v1["ats"]["issues"]: print("   issue:", i["severity"], i["message"])

r = upload("resume.docx", tok, title="Docx resume"); check("upload docx 201", r.status_code == 201, r.text)
check("docx ATS", r.json()["currentVersion"]["ats"]["overallScore"] > 0)
rid_docx = r.json()["resume"]["id"]
r = call("GET", "/api/resumes", token=tok, expect=200); check("list has 2", r.json()["totalItems"] == 2, r.text)
check("list shows atsScore", all(x["atsScore"] for x in r.json()["items"]))
call("GET", f"/api/resumes/{rid}", token=tok2, expect=404)       # IDOR
call("GET", "/api/resumes", token=tok2, expect=200)
check("other user sees none", call("GET", "/api/resumes", token=tok2).json()["totalItems"] == 0)
call("GET", f"/api/resumes/{rid}/versions/{v1['id']}/file", token=tok, expect=200)
call("GET", f"/api/resumes/{rid}/versions/{v1['id']}/file", token=tok2, expect=404)
r = call("PATCH", f"/api/resumes/{rid}", token=tok, json={"title": "Priya Main Resume"}, expect=200)

# new version by upload + duplicate rejection
with open(T + "resume_v2.pdf", "rb") as f:
    r = call("POST", f"/api/resumes/{rid}/versions", token=tok, files={"file": ("resume_v2.pdf", f)}, data={"label": "Tweaked payment bullet"}, expect=201)
v2 = r.json(); check("v2 number 2 + current", v2["versionNumber"] == 2 and v2["current"])
with open(T + "resume_v2.pdf", "rb") as f:
    call("POST", f"/api/resumes/{rid}/versions", token=tok, files={"file": ("again.pdf", f)}, expect=409)
r = call("GET", f"/api/resumes/{rid}/compare?from={v1['id']}&to={v2['id']}", token=tok, expect=200)
cmp = r.json(); check("diff finds changes", cmp["addedLines"] >= 1 and cmp["removedLines"] >= 1, cmp)
check("diff rows non-empty", any(x["type"] in ("CHANGE", "INSERT", "DELETE") for x in cmp["rows"]))
call("POST", f"/api/resumes/{rid}/versions/{v1['id']}/activate", token=tok, expect=200)
check("activate switches current", call("GET", f"/api/resumes/{rid}", token=tok).json()["resume"]["currentVersionId"] == v1["id"])

# manual edit creates version 3
edited = v1["extractedText"].replace("Responsible for maintaining", "Maintained")
r = call("POST", f"/api/resumes/{rid}/versions/edit", token=tok, json={"label": "Manual", "text": edited}, expect=201)
check("edit -> v3", r.json()["versionNumber"] == 3, r.text)
call("POST", f"/api/resumes/{rid}/versions/edit", token=tok, json={"label": "bad"}, expect=400)
call("POST", f"/api/resumes/{rid}/versions/edit", token=tok, json={"text": "x", "document": {"summary": "y"}}, expect=400)
v3 = r.json()
# ATS endpoints
call("GET", f"/api/resumes/{rid}/versions/{v1['id']}/ats", token=tok, expect=200)
call("GET", f"/api/resumes/{rid}/versions/{v1['id']}/ats", token=tok2, expect=404)
r = call("POST", f"/api/resumes/{rid}/versions/{v1['id']}/ats?ai=true", token=tok, expect=200); check("ATS ai summary", bool(r.json()["aiSummary"]), r.text)
check("ATS history", len(call("GET", f"/api/resumes/{rid}/versions/{v1['id']}/ats/history", token=tok).json()) >= 2)

# ---------------- job descriptions + matching ----------------
JD = """Senior Java Backend Engineer
About you
Requirements:
- 5+ years of experience with Java and Spring Boot building REST APIs
- Strong knowledge of PostgreSQL and SQL
- Experience with Docker and CI/CD
- Excellent communication skills
Nice to have:
- GraphQL, Terraform and Kubernetes
- Leadership experience
We are a fast growing company building payment products used by millions."""
r = call("POST", "/api/job-descriptions", token=tok, json={"title": "Senior Java Backend Engineer", "company": "Initech", "rawText": JD}, expect=201)
jd = r.json(); jid = jd["id"]
check("JD skills extracted", jd["requirements"] and len(jd["requirements"]["hardSkills"]) >= 3, jd["requirements"])
check("JD seniority", jd["seniority"] == "SENIOR", jd["seniority"])
call("POST", "/api/job-descriptions", token=tok, json={"title": "t", "rawText": "short"}, expect=400)
r = call("POST", "/api/job-descriptions", token=tok, json={"title": "Senior Java Backend Engineer", "company": "Initech", "rawText": JD}, expect=201)
call("GET", f"/api/job-descriptions/{jid}", token=tok2, expect=404)
r = call("POST", "/api/matches", token=tok, json={"resumeId": rid, "jobDescriptionId": jid}, expect=201)
m = r.json(); mid = m["id"]
print("   match:", m["matchPercentage"], m["verdict"], "hard", m["hardSkillCoverage"], "soft", m["softSkillCoverage"], "sem", m["semanticScore"], "title", m["titleFitScore"], m["counts"])
for sk in m["skills"]: print("     ", sk["status"], sk["importance"], sk["skill"], "|", (sk["evidence"] or "")[:60])
check("match pct in range", 0 < float(m["matchPercentage"]) <= 100)
statuses = {s["normalizedName"]: s["status"] for s in m["skills"]}
check("Java matched", statuses.get("Java") == "MATCHED", statuses)
check("PostgreSQL partial (MySQL related)", statuses.get("PostgreSQL") == "PARTIAL", statuses)
check("GraphQL partial via REST", statuses.get("GraphQL") in ("PARTIAL","MISSING"), statuses)
check("recommendations present", len(m["recommendations"]) >= 1)
call("POST", "/api/matches", token=tok2, json={"resumeId": rid, "jobDescriptionId": jid}, expect=404)
call("GET", f"/api/matches/{mid}", token=tok2, expect=404)
check("match history", call("GET", f"/api/matches?jobDescriptionId={jid}", token=tok).json()["totalItems"] >= 1)

# ---------------- optimizer ----------------
r = call("POST", "/api/optimizer/bullets/analyze", token=tok, json={"resumeId": rid, "jobDescriptionId": jid}, expect=200)
an = r.json(); print("   scanned", an["bulletsScanned"], "weak", an["weakBullets"], "rewrites", len(an["rewrites"]))
check("weak bullets found", an["weakBullets"] >= 2 and len(an["rewrites"]) >= 2, an)
rw = an["rewrites"]
needs = [x for x in rw if x["needsUserMetric"]]
print("   example:", rw[0]["originalText"], "=>", rw[0]["rewrittenText"], rw[0]["impactScoreBefore"], "->", rw[0]["impactScoreAfter"])
if needs:
    call("POST", "/api/optimizer/bullets/apply", token=tok, json={"resumeId": rid, "choices": [{"rewriteId": needs[0]["id"]}]}, expect=400)  # placeholder not filled
    filled = needs[0]["rewrittenText"].replace("[X%]", "30%")
    r = call("POST", "/api/optimizer/bullets/apply", token=tok, json={"resumeId": rid, "choices": [{"rewriteId": needs[0]["id"], "finalText": filled}], "label": "AI pass"}, expect=201)
    nv = r.json(); check("AI version created", nv["source"] == "AI_REWRITE" and filled in nv["extractedText"], nv["extractedText"][:300])
    check("AI version ATS", nv["ats"] is not None)
    call("POST", "/api/optimizer/bullets/apply", token=tok, json={"resumeId": rid, "choices": [{"rewriteId": needs[0]["id"], "finalText": filled}]}, expect=409)
if len(rw) > 1:
    call("POST", f"/api/optimizer/bullets/{rw[1]['id']}/reject", token=tok, expect=200)
    call("POST", f"/api/optimizer/bullets/{rw[1]['id']}/reject", token=tok2, expect=404)
call("GET", f"/api/optimizer/bullets?resumeId={rid}", token=tok, expect=200)
r = call("POST", "/api/optimizer/cover-letters", token=tok, json={"resumeId": rid, "jobDescriptionId": jid, "tone": "CONCISE"}, expect=201)
cl = r.json(); check("cover letter text", "Dear Hiring Team" in cl["content"])
call("PUT", f"/api/documents/{cl['id']}", token=tok, json={"content": cl["content"] + "\nP.S. edited"}, expect=200)
r = call("POST", "/api/optimizer/interview-questions", token=tok, json={"resumeId": rid, "jobDescriptionId": jid, "count": 5}, expect=201)
iq = r.json(); check("5 interview questions", len(iq["structured"]["questions"]) == 5, iq)
check("docs list", call("GET", "/api/documents", token=tok).json()["totalItems"] == 2)
call("GET", f"/api/documents/{cl['id']}", token=tok2, expect=404)

# ---------------- tracker ----------------
ids = []
for i, (co, st) in enumerate([("Initech", None), ("Globex", None), ("Hooli", "APPLIED")]):
    body = {"companyName": co, "positionTitle": "Java Dev", "jobDescriptionId": jid if i == 0 else None}
    if st: body["status"] = st
    r = call("POST", "/api/applications", token=tok, json=body, expect=201); ids.append(r.json()["id"])
call("POST", "/api/applications", token=tok, json={"companyName": "", "positionTitle": "x"}, expect=400)
r = call("GET", "/api/applications/board", token=tok, expect=200)
cols = {c["status"]: c for c in r.json()["columns"]}
check("board columns", list(cols) == ["SAVED", "APPLIED", "INTERVIEWING", "OFFERED", "REJECTED"] and cols["SAVED"]["count"] == 2 and cols["APPLIED"]["count"] == 1, {k: v["count"] for k, v in cols.items()})
r = call("POST", f"/api/applications/{ids[1]}/move", token=tok, json={"status": "APPLIED", "position": 0}, expect=200)
r = call("POST", f"/api/applications/{ids[0]}/move", token=tok, json={"status": "APPLIED", "position": 1}, expect=200)
cols = {c["status"]: c for c in call("GET", "/api/applications/board", token=tok).json()["columns"]}
order = [a["id"] for a in cols["APPLIED"]["applications"]]
check("drag order respected", order == [ids[1], ids[0], ids[2]], order)
r = call("POST", f"/api/applications/{ids[2]}/move", token=tok, json={"status": "INTERVIEWING", "position": 0, "note": "phone screen"}, expect=200)
d = call("GET", f"/api/applications/{ids[2]}", token=tok, expect=200).json()
check("history recorded", [h["to"] for h in d["history"]][-1] == "INTERVIEWING" and d["appliedAt"], d["history"])
call("POST", f"/api/applications/{ids[2]}/move", token=tok, json={"status": "INTERVIEWING", "position": 0, "version": 999}, expect=409)
call("POST", f"/api/applications/{ids[2]}/move", token=tok2, json={"status": "OFFERED", "position": 0}, expect=404)
st = call("GET", "/api/applications/stats", token=tok, expect=200).json(); check("stats", st["total"] == 3, st)
call("POST", f"/api/applications/from-job/{jid}", token=tok, expect=201)
call("PUT", f"/api/applications/{ids[0]}", token=tok, json={"companyName": "Initech", "positionTitle": "Sr Java", "nextFollowUpAt": "2026-01-01T00:00:00Z"}, expect=200)
check("follow-ups overdue", len(call("GET", "/api/applications/follow-ups?days=7", token=tok).json()) >= 1)
call("DELETE", f"/api/applications/{ids[1]}", token=tok, expect=204)

# ---------------- chat ----------------
r = call("POST", "/api/chat/sessions", token=tok, json={"resumeId": rid, "jobDescriptionId": jid}, expect=201); sid = r.json()["id"]
r = call("POST", f"/api/chat/sessions/{sid}/messages", token=tok, json={"content": "How can I improve my payment service bullet?"}, expect=200)
check("chat reply", r.json()["assistantMessage"]["content"].startswith("Here is some feedback"), r.text)
r = requests.post(BASE + f"/api/chat/sessions/{sid}/messages/stream", headers={"Authorization": "Bearer " + tok, "Accept": "text/event-stream"}, json={"content": "And my summary?"}, stream=True, timeout=60)
body = r.text
check("stream status 200", r.status_code == 200, body[:300])
check("stream has token + done events", "event:token" in body and "event:done" in body, body[:500])
d = call("GET", f"/api/chat/sessions/{sid}", token=tok, expect=200).json()
check("chat history 4 msgs", len(d["messages"]) == 4, [m["role"] for m in d["messages"]])
call("GET", f"/api/chat/sessions/{sid}", token=tok2, expect=404)
call("POST", f"/api/chat/sessions/{sid}/messages", token=tok2, json={"content": "hi"}, expect=404)

# ---------------- github ----------------
r = call("POST", "/api/github/analyses", token=tok, json={}, expect=202); job = r.json()
for _ in range(40):
    j = call("GET", f"/api/jobs/{job['jobId']}", token=tok).json()
    if j["status"] in ("COMPLETED", "FAILED"): break
    time.sleep(0.5)
check("github job completed", j["status"] == "COMPLETED", j)
ga = call("GET", f"/api/github/analyses/{job['analysisId']}", token=tok, expect=200).json()
print("   github:", ga["overallScore"], ga["detectedSkills"][:6], [r["name"] for r in ga["repositories"]])
check("fork excluded, 2 repos", sorted(r["name"] for r in ga["repositories"]) == ["dotfiles", "resume-analyzer"], ga["repositories"])
ra = next(r for r in ga["repositories"] if r["name"] == "resume-analyzer")
check("readme score > 60", ra["readmeQualityScore"] >= 60, ra["readmeQualityScore"])
check("AI bullet suggested", ra["suggestedBullet"].startswith("Built resume-analyzer"), ra["suggestedBullet"])
check("tech detected", "Java" in ra["technologies"] and "Spring Boot" in ra["technologies"], ra["technologies"])
r = call("POST", "/api/github/analyses", token=tok, json={}, expect=200); check("cached within 24h", r.json()["cached"] is True)
call("GET", f"/api/jobs/{job['jobId']}", token=tok2, expect=404)
r = call("POST", f"/api/github/analyses/{job['analysisId']}/to-resume", token=tok, json={"resumeId": rid, "repositoryIds": [ra["id"]]}, expect=201)
check("github project added to resume", any(p["name"] == "resume-analyzer" for p in r.json()["structuredContent"]["projects"]) and r.json()["source"] == "IMPORT", r.text[:300])
call("POST", f"/api/github/analyses/{job['analysisId']}/to-resume", token=tok, json={"resumeId": rid, "repositoryIds": [ra["id"]]}, expect=409)
r = call("POST", "/api/github/analyses", token=tok, json={"username": "ghost", "refresh": True}, expect=202); gj = r.json()
for _ in range(40):
    j = call("GET", f"/api/jobs/{gj['jobId']}", token=tok).json()
    if j["status"] in ("COMPLETED", "FAILED"): break
    time.sleep(0.5)
check("unknown github user fails cleanly", j["status"] == "FAILED" and "not found" in (j["errorMessage"] or "").lower(), j)
# match now can use github skills
m2 = call("POST", "/api/matches", token=tok, json={"resumeId": rid, "jobDescriptionId": jid, "includeAi": False}, expect=201).json()
check("match without AI uses rule-based recs", m2["aiProvider"] is None and m2["recommendations"], m2["recommendations"])

# ---------------- export ----------------
tp = call("GET", "/api/templates", token=tok, expect=200).json(); check("3 templates", len(tp) == 3, tp)
for code in ("classic", "modern", "technical"):
    r = call("POST", "/api/exports", token=tok, json={"resumeId": rid, "templateCode": code, "settings": {"accentColor": "#1f4e79", "fontSizePt": 10}}, expect=201)
    ex = r.json(); print("   export", code, ex["pageCount"], "pages", ex["fileSizeBytes"], "bytes self-check", ex["atsSelfCheckScore"])
    check(f"{code} self-check >= 90", ex["atsSelfCheckScore"] >= 90, ex)
    pdf = call("GET", ex["downloadUrl"], token=tok, expect=200)
    check(f"{code} is a PDF", pdf.content[:4] == b"%PDF" and pdf.headers["content-type"] == "application/pdf")
    open(T + f"export_{code}.pdf", "wb").write(pdf.content)
call("GET", ex["downloadUrl"], token=tok2, expect=404)
call("POST", "/api/exports", token=tok, json={"resumeId": rid, "templateCode": "nope"}, expect=404)
call("POST", "/api/exports", token=tok, json={"resumeId": rid, "templateCode": "classic", "settings": {"accentColor": "red"}}, expect=400)

# ---------------- cleanup / misc ----------------
call("DELETE", f"/api/resumes/{rid_docx}", token=tok, expect=204)
call("GET", f"/api/resumes/{rid_docx}", token=tok, expect=404)
call("POST", "/api/auth/logout", json={"refreshToken": refresh}, expect=204)
call("POST", "/api/auth/refresh", json={"refreshToken": refresh}, expect=401)
r = call("GET", "/swagger-ui/index.html"); check("swagger ui public", r.status_code == 200)
r = call("GET", "/v3/api-docs"); check("openapi docs public", r.status_code == 200 and "/api/resumes" in r.text)
r = call("GET", "/actuator/health"); check("health public", r.status_code == 200)
call("GET", "/api/nope", token=tok, expect=404)

print("\nFAILED:" if FAILS else "\nALL PASSED", FAILS if FAILS else "")
sys.exit(1 if FAILS else 0)

export interface Paged<T> { items: T[]; page: number; size: number; totalItems: number; totalPages: number }

export interface User { id: number; email: string; fullName: string; headline: string | null; githubUsername: string | null; role: string; emailVerified: boolean; createdAt: string; lastLoginAt: string | null }
export interface AuthResponse { accessToken: string; refreshToken: string; tokenType: string; expiresIn: number; user: User }

export interface ResumeSummary { id: number; title: string; targetRole: string | null; archived: boolean; currentVersionId: number | null; currentVersionNumber: number | null; atsScore: number | null; createdAt: string; updatedAt: string }
export interface VersionSummary { id: number; versionNumber: number; label: string | null; source: "UPLOAD" | "MANUAL_EDIT" | "AI_REWRITE" | "IMPORT"; originalFilename: string | null; fileSizeBytes: number | null; pageCount: number | null; wordCount: number | null; parentVersionId: number | null; current: boolean; atsScore: number | null; createdAt: string }
export interface AtsIssue { ruleId: string; category: string; severity: "ERROR" | "WARNING" | "INFO"; message: string; suggestion: string | null }
export interface AtsResult { id: number; resumeVersionId: number; overallScore: number; grade: string; categories: { name: string; score: number; max: number }[]; detectedTitle: string | null; detectedSections: string[]; issues: AtsIssue[]; aiSummary: string | null; analyzerVersion: string; createdAt: string }
export interface VersionDetail { id: number; resumeId: number; versionNumber: number; label: string | null; source: string; originalFilename: string | null; contentType: string | null; fileSizeBytes: number | null; pageCount: number | null; wordCount: number | null; parentVersionId: number | null; current: boolean; hasOriginalFile: boolean; extractedText: string; structuredContent: unknown; ats: AtsResult | null; createdAt: string }
export interface ResumeDetail { resume: ResumeSummary; versions: VersionSummary[]; currentVersion: VersionDetail }
export interface DiffRow { type: "EQUAL" | "INSERT" | "DELETE" | "CHANGE"; left: string | null; right: string | null }
export interface CompareResponse { fromVersionId: number; toVersionId: number; fromVersionNumber: number; toVersionNumber: number; fromAtsScore: number | null; toAtsScore: number | null; wordCountDelta: number | null; addedLines: number; removedLines: number; unchangedLines: number; rows: DiffRow[] }

export interface Requirement { name: string; importance: "REQUIRED" | "PREFERRED" }
export interface JobRequirements { hardSkills: Requirement[]; softSkills: Requirement[]; tools: Requirement[]; certifications: Requirement[]; seniority: string | null }
export interface JobDescriptionSummary { id: number; title: string; company: string | null; location: string | null; seniority: string | null; createdAt: string }
export interface JobDescription extends JobDescriptionSummary { sourceUrl: string | null; rawText: string; requirements: JobRequirements | null; updatedAt: string }

export interface SkillGap { skill: string; normalizedName: string; type: string; importance: "REQUIRED" | "PREFERRED"; status: "MATCHED" | "PARTIAL" | "MISSING"; evidence: string | null; evidenceSource: string | null }
export interface MatchResponse { id: number; resumeId: number; resumeVersionId: number; resumeVersionNumber: number; jobDescriptionId: number; jobTitle: string; company: string | null; matchPercentage: number; verdict: string; hardSkillCoverage: number; softSkillCoverage: number; semanticScore: number; titleFitScore: number; counts: { matched: number; partial: number; missing: number }; skills: SkillGap[]; missingRequired: SkillGap[]; recommendations: string[]; aiProvider: string | null; createdAt: string }
export interface MatchSummary { id: number; resumeId: number; resumeVersionId: number; jobDescriptionId: number; jobTitle: string; company: string | null; matchPercentage: number; createdAt: string }

export interface BulletRewrite { id: number; resumeVersionId: number; section: string | null; originalText: string; rewrittenText: string; needsUserMetric: boolean; impactScoreBefore: number | null; impactScoreAfter: number | null; status: "SUGGESTED" | "ACCEPTED" | "REJECTED"; appliedInVersionId: number | null; createdAt: string }
export interface AnalyzeBulletsResponse { resumeVersionId: number; bulletsScanned: number; weakBullets: number; rewrites: BulletRewrite[]; note: string | null }
export interface GeneratedDoc { id: number; type: "COVER_LETTER" | "INTERVIEW_QUESTIONS"; title: string; content: string; structured: { questions?: { question: string; category: string; whatInterviewersLookFor: string; answerOutline: string }[] } | null; tone: string | null; resumeVersionId: number; jobDescriptionId: number | null; applicationId: number | null; modelName: string | null; createdAt: string; updatedAt: string }
export interface DocSummary { id: number; type: "COVER_LETTER" | "INTERVIEW_QUESTIONS"; title: string; jobDescriptionId: number | null; applicationId: number | null; createdAt: string }

export type AppStatus = "SAVED" | "APPLIED" | "INTERVIEWING" | "OFFERED" | "REJECTED";
export interface HistoryEntry { from: AppStatus | null; to: AppStatus; note: string | null; changedAt: string }
export interface Application { id: number; companyName: string; positionTitle: string; jobUrl: string | null; location: string | null; salaryRange: string | null; status: AppStatus; boardOrder: number; appliedAt: string | null; nextFollowUpAt: string | null; notes: string | null; jobDescriptionId: number | null; resumeId: number | null; resumeVersionId: number | null; version: number; createdAt: string; updatedAt: string; history: HistoryEntry[] | null }
export interface Board { columns: { status: AppStatus; count: number; applications: Application[] }[]; total: number }
export interface TrackerStats { byStatus: Record<string, number>; total: number; active: number; interviewRatePercent: number; offerRatePercent: number }

export interface ChatSession { id: number; title: string; resumeId: number | null; resumeVersionId: number | null; jobDescriptionId: number | null; lastMessageAt: string | null; createdAt: string }
export interface ChatMessage { id: number; role: "USER" | "ASSISTANT" | "SYSTEM"; content: string; createdAt: string }
export interface ChatSessionDetail { session: ChatSession; messages: ChatMessage[] }

export type Processing = "PENDING" | "RUNNING" | "COMPLETED" | "FAILED";
export interface Job { id: string; type: string; status: Processing; progress: number; resultType: string | null; resultId: number | null; errorMessage: string | null; createdAt: string; completedAt: string | null }
export interface GithubRepo { id: number; name: string; url: string; description: string | null; primaryLanguage: string | null; languages: Record<string, number> | null; stars: number; forks: number; lastPushedAt: string | null; readmeQualityScore: number | null; complexityScore: number | null; technologies: string[] | null; suggestedBullet: string | null; includedInResume: boolean }
export interface GithubAnalysis { id: number; githubUsername: string; status: Processing; overallScore: number | null; profileSummary: Record<string, unknown> | null; detectedSkills: string[] | null; errorMessage: string | null; analyzedAt: string | null; createdAt: string; repositories: GithubRepo[] | null }

export interface Template { id: number; code: string; name: string; description: string | null; atsSafe: boolean }
export interface ExportRecord { id: number; status: Processing; templateCode: string; resumeVersionId: number; pageCount: number | null; fileSizeBytes: number | null; atsSelfCheckScore: number | null; settings: Record<string, unknown> | null; errorMessage: string | null; downloadUrl: string | null; createdAt: string }

package com.smartresume.planner.ai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartresume.planner.ai.domain.GeneratedDocument;
import com.smartresume.planner.ai.domain.GeneratedDocumentType;
import com.smartresume.planner.ai.dto.OptimizerDtos.CoverLetterRequest;
import com.smartresume.planner.ai.dto.OptimizerDtos.DocumentResponse;
import com.smartresume.planner.ai.dto.OptimizerDtos.DocumentSummary;
import com.smartresume.planner.ai.dto.OptimizerDtos.InterviewQuestionsRequest;
import com.smartresume.planner.ai.dto.OptimizerDtos.Tone;
import com.smartresume.planner.ai.dto.OptimizerDtos.UpdateDocumentRequest;
import com.smartresume.planner.ai.repository.GeneratedDocumentRepository;
import com.smartresume.planner.common.exception.ApiException;
import com.smartresume.planner.common.util.Texts;
import com.smartresume.planner.common.web.PagedResponse;
import com.smartresume.planner.matching.domain.JobDescription;
import com.smartresume.planner.matching.repository.JobDescriptionRepository;
import com.smartresume.planner.resume.repository.ResumeVersionRepository;
import com.smartresume.planner.resume.service.ResumeLookup;
import com.smartresume.planner.resume.service.ResumeLookup.Snapshot;
import com.smartresume.planner.tracker.domain.JobApplication;
import com.smartresume.planner.tracker.repository.JobApplicationRepository;
import com.smartresume.planner.user.repository.UserRepository;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Role-tailored cover letters and interview practice questions, saved so they can be edited and reused. */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentGenerationService {

    private final ResumeLookup resumeLookup;
    private final ResumeVersionRepository versionRepository;
    private final JobDescriptionRepository jobRepository;
    private final JobApplicationRepository applicationRepository;
    private final GeneratedDocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final AiGateway ai;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate tx;

    public record Question(String question, String category, String whatInterviewersLookFor, String answerOutline) { }

    public record QuestionSet(List<Question> questions) { }

    private record JobData(Long id, String title, String company, String text) { }

    // ------------------------------------------------------------------ cover letter

    public DocumentResponse coverLetter(Long userId, CoverLetterRequest request) {
        Snapshot resume = resumeLookup.snapshot(userId, request.resumeId(), request.versionId());
        JobData job = loadJob(userId, request.jobDescriptionId());
        verifyApplication(userId, request.applicationId());
        Tone tone = request.tone() == null ? Tone.PROFESSIONAL : request.tone();

        String candidate = resume.document().contact().fullName();
        String system = AiPrompts.system("""
                You write tailored cover letters. Output ONLY the letter text, no markdown, no subject line, no commentary.
                Rules:
                - 250 to 350 words, 3 or 4 short paragraphs, tone: %s.
                - Open with the specific role%s and why the candidate fits it.
                - Use 2 or 3 concrete achievements that appear in the resume and relate to the job's requirements.
                - Use only facts present in the resume. Never invent employers, numbers or skills.
                - Address it to "Hiring Team" unless a name is given. End with a short call to action and the sign-off "Sincerely," \
                followed by %s.""".formatted(tone.name().toLowerCase(),
                job.company() == null ? "" : " and company",
                candidate == null || candidate.isBlank() ? "the candidate's name" : candidate));
        String user = "Role: " + job.title() + (job.company() == null ? "" : " at " + job.company()) + "\n\n"
                + AiPrompts.jobDescription(job.text()) + "\n\n" + AiPrompts.resume(resume.text())
                + (Texts.blankToNull(request.notes()) == null ? ""
                : "\n\n<user_message>\nExtra points the candidate wants included:\n" + request.notes() + "\n</user_message>");
        String letter = ai.text(userId, "COVER_LETTER", system, user);

        String title = "Cover letter - " + job.title() + (job.company() == null ? "" : " at " + job.company());
        return save(userId, resume.versionId(), job.id(), request.applicationId(), GeneratedDocumentType.COVER_LETTER,
                title, letter, null, tone.name());
    }

    // ------------------------------------------------------------------ interview questions

    public DocumentResponse interviewQuestions(Long userId, InterviewQuestionsRequest request) {
        Snapshot resume = resumeLookup.snapshot(userId, request.resumeId(), request.versionId());
        JobData job = loadJob(userId, request.jobDescriptionId());
        verifyApplication(userId, request.applicationId());
        int count = request.count() == null ? 8 : request.count();

        String system = AiPrompts.system("""
                You prepare candidates for interviews and answer with JSON only:
                {"questions":[{"question":"...","category":"TECHNICAL|BEHAVIORAL|SITUATIONAL|ROLE_SPECIFIC",\
                "whatInterviewersLookFor":"...","answerOutline":"..."}]}
                Rules:
                - Exactly %d questions, a mix of categories, tailored to this job and this candidate's actual background.
                - Include at least one question probing a gap or weaker area between the resume and the job.
                - answerOutline: 2 to 3 sentences guiding the answer using real experience from the resume (do not invent any).""".formatted(count));
        String user = "Role: " + job.title() + (job.company() == null ? "" : " at " + job.company()) + "\n\n"
                + AiPrompts.jobDescription(job.text()) + "\n\n" + AiPrompts.resume(resume.text());
        QuestionSet set = ai.structured(userId, "INTERVIEW_QUESTIONS", system, user, QuestionSet.class);
        List<Question> questions = set.questions() == null ? List.of() : set.questions().stream()
                .filter(q -> q != null && q.question() != null && !q.question().isBlank()).limit(15).toList();
        if (questions.isEmpty()) {
            throw ApiException.unavailable("AI_BAD_OUTPUT", "The AI did not return any questions. Please try again.");
        }

        StringBuilder md = new StringBuilder();
        int n = 1;
        for (Question q : questions) {
            md.append(n++).append(". **").append(q.question().strip()).append("**");
            if (q.category() != null) {
                md.append(" _(").append(q.category()).append(")_");
            }
            md.append('\n');
            if (Texts.blankToNull(q.whatInterviewersLookFor()) != null) {
                md.append("   - Looking for: ").append(q.whatInterviewersLookFor().strip()).append('\n');
            }
            if (Texts.blankToNull(q.answerOutline()) != null) {
                md.append("   - How to answer: ").append(q.answerOutline().strip()).append('\n');
            }
            md.append('\n');
        }
        Map<String, Object> json = objectMapper.convertValue(new QuestionSet(questions), new TypeReference<>() { });
        String title = "Interview questions - " + job.title() + (job.company() == null ? "" : " at " + job.company());
        return save(userId, resume.versionId(), job.id(), request.applicationId(),
                GeneratedDocumentType.INTERVIEW_QUESTIONS, title, md.toString().strip(), json, null);
    }

    // ------------------------------------------------------------------ CRUD on saved documents

    public PagedResponse<DocumentSummary> list(Long userId, GeneratedDocumentType type, Long applicationId, int page,
                                               int size) {
        return tx.execute(s -> PagedResponse.of(documentRepository.search(userId, type, applicationId,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                        Sort.by(Sort.Direction.DESC, "createdAt"))), DocumentSummary::from));
    }

    public DocumentResponse get(Long userId, Long id) {
        return tx.execute(s -> DocumentResponse.from(load(userId, id)));
    }

    public DocumentResponse update(Long userId, Long id, UpdateDocumentRequest request) {
        return tx.execute(s -> {
            GeneratedDocument d = load(userId, id);
            if (request.title() != null && !request.title().isBlank()) {
                d.setTitle(request.title().trim());
            }
            d.setContent(request.content().strip());
            return DocumentResponse.from(documentRepository.saveAndFlush(d));
        });
    }

    public void delete(Long userId, Long id) {
        tx.executeWithoutResult(s -> documentRepository.delete(load(userId, id)));
    }

    // ------------------------------------------------------------------ helpers

    private DocumentResponse save(Long userId, Long versionId, Long jobId, Long applicationId,
                                  GeneratedDocumentType type, String title, String content,
                                  Map<String, Object> json, String tone) {
        return tx.execute(s -> {
            GeneratedDocument d = new GeneratedDocument(userRepository.getReferenceById(userId),
                    versionRepository.getReferenceById(versionId), type, Texts.truncate(title, 200), content);
            d.setJobDescription(jobRepository.getReferenceById(jobId));
            if (applicationId != null) {
                d.setApplication(applicationRepository.getReferenceById(applicationId));
            }
            d.setContentJson(json);
            d.setTone(tone);
            d.setModelName(ai.provider());
            documentRepository.saveAndFlush(d);
            return DocumentResponse.from(d);
        });
    }

    private JobData loadJob(Long userId, Long jobId) {
        return tx.execute(s -> {
            JobDescription jd = jobRepository.findByIdAndUserId(jobId, userId)
                    .orElseThrow(() -> ApiException.notFound("Job description"));
            return new JobData(jd.getId(), jd.getTitle(), jd.getCompany(), jd.getRawText());
        });
    }

    private void verifyApplication(Long userId, Long applicationId) {
        if (applicationId != null) {
            tx.executeWithoutResult(s -> {
                JobApplication a = applicationRepository.findByIdAndUserId(applicationId, userId)
                        .orElseThrow(() -> ApiException.notFound("Application"));
            });
        }
    }

    private GeneratedDocument load(Long userId, Long id) {
        return documentRepository.findByIdAndUserId(id, userId).orElseThrow(() -> ApiException.notFound("Document"));
    }
}

package com.smartresume.planner.matching.service;

import com.smartresume.planner.common.exception.ApiException;
import com.smartresume.planner.common.util.Hashing;
import com.smartresume.planner.common.util.Texts;
import com.smartresume.planner.common.web.PagedResponse;
import com.smartresume.planner.matching.domain.JobDescription;
import com.smartresume.planner.matching.domain.JobRequirements;
import com.smartresume.planner.matching.dto.MatchingDtos.JobDescriptionRequest;
import com.smartresume.planner.matching.dto.MatchingDtos.JobDescriptionResponse;
import com.smartresume.planner.matching.dto.MatchingDtos.JobDescriptionSummary;
import com.smartresume.planner.matching.repository.JobDescriptionRepository;
import com.smartresume.planner.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobDescriptionService {

    private final JobDescriptionRepository repository;
    private final UserRepository userRepository;
    private final JobRequirementsExtractor extractor;
    private final TransactionTemplate tx;

    /** Saves a job description and extracts its skills (cached by text hash, so re-pasting is free). */
    public JobDescriptionResponse create(Long userId, JobDescriptionRequest request) {
        String text = request.rawText().strip();
        String hash = Hashing.sha256Hex(text);
        JobDescription cached = tx.execute(s -> repository.findFirstByUserIdAndTextHashOrderByIdDesc(userId, hash)
                .orElse(null));
        JobRequirements requirements = cached != null && cached.getExtractedSkills() != null
                ? cached.getExtractedSkills()
                : extractor.extract(userId, request.title(), text);

        return tx.execute(s -> {
            JobDescription jd = new JobDescription(userRepository.getReferenceById(userId), request.title().trim(),
                    Texts.blankToNull(request.company()), text, hash);
            jd.setLocation(Texts.blankToNull(request.location()));
            jd.setSourceUrl(Texts.blankToNull(request.sourceUrl()));
            jd.setExtractedSkills(requirements);
            jd.setSeniority(requirements.seniority());
            repository.saveAndFlush(jd);
            log.info("Saved job description id={} for user id={}", jd.getId(), userId);
            return JobDescriptionResponse.from(jd);
        });
    }

    public JobDescriptionResponse update(Long userId, Long id, JobDescriptionRequest request) {
        String text = request.rawText().strip();
        String hash = Hashing.sha256Hex(text);
        JobDescription existing = tx.execute(s -> load(userId, id));
        boolean textChanged = !hash.equals(existing.getTextHash());
        JobRequirements requirements = textChanged ? extractor.extract(userId, request.title(), text)
                : existing.getExtractedSkills();
        return tx.execute(s -> {
            JobDescription jd = load(userId, id);
            jd.setTitle(request.title().trim());
            jd.setCompany(Texts.blankToNull(request.company()));
            jd.setLocation(Texts.blankToNull(request.location()));
            jd.setSourceUrl(Texts.blankToNull(request.sourceUrl()));
            if (textChanged) {
                jd.setRawText(text);
                jd.setTextHash(hash);
                jd.setExtractedSkills(requirements);
                jd.setSeniority(requirements.seniority());
            }
            repository.saveAndFlush(jd);
            return JobDescriptionResponse.from(jd);
        });
    }

    public JobDescriptionResponse get(Long userId, Long id) {
        return tx.execute(s -> JobDescriptionResponse.from(load(userId, id)));
    }

    public PagedResponse<JobDescriptionSummary> list(Long userId, int page, int size) {
        return tx.execute(s -> PagedResponse.of(repository.findByUserId(userId,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                        Sort.by(Sort.Direction.DESC, "createdAt"))), JobDescriptionSummary::from));
    }

    public void delete(Long userId, Long id) {
        tx.executeWithoutResult(s -> repository.delete(load(userId, id)));
    }

    /** Re-extracts skills (e.g. after fixing the AI key). */
    public JobDescriptionResponse reextract(Long userId, Long id) {
        JobDescription existing = tx.execute(s -> load(userId, id));
        JobRequirements requirements = extractor.extract(userId, existing.getTitle(), existing.getRawText());
        return tx.execute(s -> {
            JobDescription jd = load(userId, id);
            jd.setExtractedSkills(requirements);
            jd.setSeniority(requirements.seniority());
            repository.saveAndFlush(jd);
            return JobDescriptionResponse.from(jd);
        });
    }

    JobDescription load(Long userId, Long id) {
        return repository.findByIdAndUserId(id, userId).orElseThrow(() -> ApiException.notFound("Job description"));
    }
}

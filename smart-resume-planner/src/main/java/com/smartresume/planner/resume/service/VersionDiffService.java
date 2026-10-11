package com.smartresume.planner.resume.service;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.Patch;
import com.smartresume.planner.ats.repository.AtsAnalysisRepository;
import com.smartresume.planner.resume.domain.ResumeVersion;
import com.smartresume.planner.resume.dto.ResumeDtos.CompareResponse;
import com.smartresume.planner.resume.dto.ResumeDtos.DiffRow;
import com.smartresume.planner.resume.dto.ResumeDtos.DiffRow.Type;
import com.smartresume.planner.resume.repository.ResumeVersionRepository;
import com.smartresume.planner.common.exception.ApiException;
import com.smartresume.planner.ats.domain.AtsAnalysis;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Side-by-side line diff between two versions of the same resume. */
@Service
@RequiredArgsConstructor
public class VersionDiffService {

    private final ResumeVersionRepository versionRepository;
    private final AtsAnalysisRepository atsRepository;

    @Transactional(readOnly = true)
    public CompareResponse compare(Long userId, Long resumeId, Long fromId, Long toId) {
        ResumeVersion from = versionRepository.findOwned(fromId, resumeId, userId)
                .orElseThrow(() -> ApiException.notFound("Resume version"));
        ResumeVersion to = versionRepository.findOwned(toId, resumeId, userId)
                .orElseThrow(() -> ApiException.notFound("Resume version"));

        List<String> left = lines(from.getExtractedText());
        List<String> right = lines(to.getExtractedText());
        Patch<String> patch = DiffUtils.diff(left, right);

        List<DiffRow> rows = new ArrayList<>();
        int added = 0;
        int removed = 0;
        int cursor = 0;
        for (AbstractDelta<String> delta : patch.getDeltas()) {
            int srcPos = delta.getSource().getPosition();
            for (; cursor < srcPos; cursor++) {
                rows.add(new DiffRow(Type.EQUAL, left.get(cursor), left.get(cursor)));
            }
            List<String> src = delta.getSource().getLines();
            List<String> tgt = delta.getTarget().getLines();
            switch (delta.getType()) {
                case DELETE -> src.forEach(l -> rows.add(new DiffRow(Type.DELETE, l, null)));
                case INSERT -> tgt.forEach(l -> rows.add(new DiffRow(Type.INSERT, null, l)));
                default -> {
                    int n = Math.max(src.size(), tgt.size());
                    for (int i = 0; i < n; i++) {
                        String l = i < src.size() ? src.get(i) : null;
                        String r = i < tgt.size() ? tgt.get(i) : null;
                        rows.add(new DiffRow(l == null ? Type.INSERT : r == null ? Type.DELETE : Type.CHANGE, l, r));
                    }
                }
            }
            removed += src.size();
            added += tgt.size();
            cursor = srcPos + src.size();
        }
        int unchanged = left.size() - removed;
        for (; cursor < left.size(); cursor++) {
            rows.add(new DiffRow(Type.EQUAL, left.get(cursor), left.get(cursor)));
        }

        Integer fromScore = atsRepository.findFirstByResumeVersionIdOrderByIdDesc(fromId)
                .map(AtsAnalysis::getOverallScore).orElse(null);
        Integer toScore = atsRepository.findFirstByResumeVersionIdOrderByIdDesc(toId)
                .map(AtsAnalysis::getOverallScore).orElse(null);
        Integer wordDelta = from.getWordCount() == null || to.getWordCount() == null
                ? null : to.getWordCount() - from.getWordCount();
        return new CompareResponse(fromId, toId, from.getVersionNumber(), to.getVersionNumber(), fromScore,
                toScore, wordDelta, added, removed, unchanged, rows);
    }

    private static List<String> lines(String text) {
        return text == null ? List.of() : text.lines().map(String::stripTrailing).toList();
    }
}

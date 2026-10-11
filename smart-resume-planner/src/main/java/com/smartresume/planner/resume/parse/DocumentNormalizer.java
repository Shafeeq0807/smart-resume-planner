package com.smartresume.planner.resume.parse;

import com.smartresume.planner.resume.domain.ResumeDocument;
import com.smartresume.planner.resume.domain.ResumeDocument.Contact;
import com.smartresume.planner.resume.domain.ResumeDocument.Education;
import com.smartresume.planner.resume.domain.ResumeDocument.Experience;
import com.smartresume.planner.resume.domain.ResumeDocument.Project;
import java.util.ArrayList;
import java.util.List;

/** Replaces nulls with empty values and trims text, so client-supplied documents are safe to render and analyse. */
public final class DocumentNormalizer {

    private DocumentNormalizer() {
    }

    public static ResumeDocument normalize(ResumeDocument d) {
        Contact c = d.contact() == null ? new Contact("", "", "", "", "", List.of()) : d.contact();
        List<Experience> exp = new ArrayList<>();
        if (d.experience() != null) {
            for (Experience e : d.experience()) {
                exp.add(new Experience(s(e.title()), s(e.company()), s(e.location()), s(e.startDate()),
                        s(e.endDate()), list(e.bullets())));
            }
        }
        List<Education> edu = new ArrayList<>();
        if (d.education() != null) {
            for (Education e : d.education()) {
                edu.add(new Education(s(e.degree()), s(e.institution()), s(e.startDate()), s(e.endDate()),
                        s(e.details())));
            }
        }
        List<Project> projects = new ArrayList<>();
        if (d.projects() != null) {
            for (Project p : d.projects()) {
                projects.add(new Project(s(p.name()), s(p.url()), s(p.description()), list(p.bullets()),
                        list(p.technologies())));
            }
        }
        return new ResumeDocument(
                new Contact(s(c.fullName()), s(c.headline()), s(c.email()), s(c.phone()), s(c.location()),
                        list(c.links())),
                s(d.summary()), exp, edu, list(d.skills()), projects, list(d.certifications()));
    }

    private static String s(String v) {
        return v == null ? "" : v.strip();
    }

    private static List<String> list(List<String> v) {
        List<String> out = new ArrayList<>();
        if (v != null) {
            for (String s : v) {
                if (s != null && !s.isBlank()) {
                    out.add(s.strip());
                }
            }
        }
        return out;
    }
}

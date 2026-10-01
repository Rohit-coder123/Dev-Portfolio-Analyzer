package com.portfolioanalyzer.service;

import com.portfolioanalyzer.dto.external.GitHubRepoDto;
import com.portfolioanalyzer.dto.response.ResumeMatchResponse;
import com.portfolioanalyzer.dto.response.SkillMatchDto;
import com.portfolioanalyzer.exception.InvalidResumeException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Extracts text from an uploaded resume PDF (Apache PDFBox) and checks a
 * predefined list of technology keywords against both that text and the
 * developer's GitHub repository corpus (languages, topics, names,
 * descriptions). No AI/LLM is used — matching is pure regex plus a
 * deterministic evidence-strength rule for the confidence label.
 *
 * Confidence rules (per skill, only for skills actually found in the resume):
 *   HIGH   — githubEvidenceStrength >= 2 (e.g. the language appears as the
 *            primary language of 1+ repos, or is corroborated in multiple
 *            places)
 *   MEDIUM — githubEvidenceStrength == 1 (weak but present evidence, e.g.
 *            only mentioned in a repo topic/description, not as a primary
 *            language)
 *   LOW    — githubEvidenceStrength == 0 (claimed on the resume, no GitHub
 *            evidence found at all)
 */
@Service
public class ResumeMatcherService {

    /**
     * skill display name -> case-insensitive regex alternatives that count
     * as a mention of that skill. Word-boundary anchored to avoid partial
     * matches (e.g. "Java" should not match inside "JavaScript").
     */
    private static final Map<String, List<String>> SKILL_KEYWORDS = new LinkedHashMap<>();

    static {
        SKILL_KEYWORDS.put("Java", List.of("\\bjava\\b"));
        SKILL_KEYWORDS.put("JavaScript", List.of("\\bjavascript\\b", "\\bjs\\b"));
        SKILL_KEYWORDS.put("TypeScript", List.of("\\btypescript\\b", "\\bts\\b"));
        SKILL_KEYWORDS.put("Python", List.of("\\bpython\\b"));
        SKILL_KEYWORDS.put("Go", List.of("\\bgolang\\b", "\\bgo\\b"));
        SKILL_KEYWORDS.put("C++", List.of("\\bc\\+\\+\\b", "\\bcpp\\b"));
        SKILL_KEYWORDS.put("C#", List.of("\\bc#\\b", "\\bc sharp\\b", "\\bcsharp\\b"));
        SKILL_KEYWORDS.put("Ruby", List.of("\\bruby\\b"));
        SKILL_KEYWORDS.put("PHP", List.of("\\bphp\\b"));
        SKILL_KEYWORDS.put("Kotlin", List.of("\\bkotlin\\b"));
        SKILL_KEYWORDS.put("Angular", List.of("\\bangular\\b"));
        SKILL_KEYWORDS.put("React", List.of("\\breact(\\.js)?\\b"));
        SKILL_KEYWORDS.put("Vue", List.of("\\bvue(\\.js)?\\b"));
        SKILL_KEYWORDS.put("Spring Boot", List.of("\\bspring[\\s-]?boot\\b", "\\bspring\\b"));
        SKILL_KEYWORDS.put("Node.js", List.of("\\bnode(\\.js)?\\b"));
        SKILL_KEYWORDS.put("Docker", List.of("\\bdocker\\b"));
        SKILL_KEYWORDS.put("Kubernetes", List.of("\\bkubernetes\\b", "\\bk8s\\b"));
        SKILL_KEYWORDS.put("AWS", List.of("\\baws\\b", "\\bamazon web services\\b"));
        SKILL_KEYWORDS.put("PostgreSQL", List.of("\\bpostgresql\\b", "\\bpostgres\\b"));
        SKILL_KEYWORDS.put("MongoDB", List.of("\\bmongodb\\b", "\\bmongo\\b"));
        SKILL_KEYWORDS.put("SQL", List.of("\\bsql\\b"));
        SKILL_KEYWORDS.put("GraphQL", List.of("\\bgraphql\\b"));
        SKILL_KEYWORDS.put("REST API", List.of("\\brest(ful)?\\s?api\\b", "\\brest\\b"));
        SKILL_KEYWORDS.put("CI/CD", List.of("\\bci/cd\\b", "\\bcontinuous integration\\b"));
        SKILL_KEYWORDS.put("Git", List.of("\\bgit\\b"));
        SKILL_KEYWORDS.put("HTML/CSS", List.of("\\bhtml\\b", "\\bcss\\b"));
    }

    /** Extracts raw text from an uploaded PDF resume. */
    public String extractText(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidResumeException("Uploaded resume file is empty.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.equalsIgnoreCase("application/pdf")) {
            throw new InvalidResumeException("Only PDF files are supported for resume matching.");
        }

        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            if (document.isEncrypted()) {
                throw new InvalidResumeException("Encrypted/password-protected PDFs are not supported.");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            if (text == null || text.isBlank()) {
                throw new InvalidResumeException("No extractable text found in the uploaded PDF.");
            }
            return text;
        } catch (IOException e) {
            throw new InvalidResumeException("Failed to parse PDF: " + e.getMessage());
        }
    }

    /**
     * Compares extracted resume text against the user's GitHub repository
     * corpus and produces a confidence-scored match per detected skill.
     */
    public ResumeMatchResponse matchSkills(String githubUsername, String resumeText, List<GitHubRepoDto> repos) {
        String githubCorpus = buildGithubCorpus(repos);
        Set<String> primaryLanguages = repos.stream()
                .map(GitHubRepoDto::getLanguage)
                .filter(Objects::nonNull)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        List<SkillMatchDto> matches = new ArrayList<>();

        for (Map.Entry<String, List<String>> entry : SKILL_KEYWORDS.entrySet()) {
            String skillName = entry.getKey();
            List<Pattern> patterns = entry.getValue().stream()
                    .map(p -> Pattern.compile(p, Pattern.CASE_INSENSITIVE))
                    .collect(Collectors.toList());

            boolean foundInResume = matchesAny(patterns, resumeText);
            if (!foundInResume) {
                continue; // Only report on skills the resume actually claims.
            }

            int evidenceStrength = computeGithubEvidenceStrength(skillName, patterns, primaryLanguages, githubCorpus);
            boolean foundInGithub = evidenceStrength > 0;
            String confidence = evidenceStrength >= 2 ? "HIGH" : (evidenceStrength == 1 ? "MEDIUM" : "LOW");

            matches.add(SkillMatchDto.builder()
                    .skillName(skillName)
                    .foundInResume(true)
                    .foundInGithub(foundInGithub)
                    .githubEvidenceStrength(evidenceStrength)
                    .confidenceLevel(confidence)
                    .build());
        }

        int corroborated = (int) matches.stream().filter(SkillMatchDto::isFoundInGithub).count();

        return ResumeMatchResponse.builder()
                .githubUsername(githubUsername)
                .skillsDetectedInResume(matches.size())
                .skillsCorroboratedByGithub(corroborated)
                .skillMatches(matches)
                .build();
    }

    private boolean matchesAny(List<Pattern> patterns, String text) {
        for (Pattern p : patterns) {
            if (p.matcher(text).find()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Evidence strength rule:
     *   +2 for each repo where the skill is the repo's PRIMARY language
     *   +1 total if the skill appears anywhere in repo names/descriptions/topics
     * The result is not capped here; callers bucket it into HIGH/MEDIUM/LOW.
     */
    private int computeGithubEvidenceStrength(String skillName, List<Pattern> patterns,
                                               Set<String> primaryLanguages, String githubCorpus) {
        int strength = 0;

        long primaryLanguageHits = primaryLanguages.stream()
                .filter(lang -> matchesAny(patterns, lang))
                .count();
        strength += (int) (primaryLanguageHits * 2);

        if (strength == 0 && matchesAny(patterns, githubCorpus)) {
            strength += 1;
        }

        return strength;
    }

    private String buildGithubCorpus(List<GitHubRepoDto> repos) {
        StringBuilder sb = new StringBuilder();
        for (GitHubRepoDto repo : repos) {
            if (repo.getName() != null) sb.append(repo.getName()).append(' ');
            if (repo.getDescription() != null) sb.append(repo.getDescription()).append(' ');
            if (repo.getLanguage() != null) sb.append(repo.getLanguage()).append(' ');
            if (repo.getTopics() != null) sb.append(String.join(" ", repo.getTopics())).append(' ');
        }
        return sb.toString();
    }
}

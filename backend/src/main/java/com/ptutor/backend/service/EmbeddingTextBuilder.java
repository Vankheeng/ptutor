package com.ptutor.backend.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.ptutor.backend.entity.Grade;
import com.ptutor.backend.entity.Student;
import com.ptutor.backend.entity.StudyingRequest;
import com.ptutor.backend.entity.TeachingRequest;
import com.ptutor.backend.entity.Tutor;

/**
 * Builds the text that is embedded for a posting or a profile. Empty fields are left out, names
 * and contact details never appear, and phone numbers / e-mails typed into free text are removed.
 */
@Component
public class EmbeddingTextBuilder {

    /** The AI service truncates to 512 tokens; this only keeps requests small. */
    static final int MAX_CHARS = 6_000;

    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+");
    private static final Pattern PHONE = Pattern.compile("(?<![\\d])(\\+?84|0)(?:[ .-]?\\d){8,10}(?![\\d])");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    /** A teaching posting: the posting itself followed by the tutor profile behind it. */
    public String forTeachingRequest(TeachingRequest request) {
        List<String> lines = new ArrayList<>();
        String subject = request.getSubject() != null
                ? request.getSubject().getName() : request.getCustomSubjectName();
        String grades = request.getGradeAssociations().stream()
                .map(association -> association.getGrade())
                .filter(Objects::nonNull)
                .map(Grade::getName)
                .map(this::clean)
                .filter(name -> !name.isEmpty())
                .distinct()
                .collect(Collectors.joining(", "));
        lines.add(sentences(
                labelled("Môn", subject),
                labelled("dạy", grades),
                labelled("Tin đăng", sentences(request.getTitle(), request.getDescription(), request.getNote()))));
        lines.addAll(tutorLines(request.getTutor()));
        return finish(lines);
    }

    /** A studying posting: the posting itself followed by the student profile behind it. */
    public String forStudyingRequest(StudyingRequest request) {
        List<String> lines = new ArrayList<>();
        lines.add(sentences(
                labelled("Môn", request.getSubject() == null ? null : request.getSubject().getName()),
                request.getGrade() == null ? null : request.getGrade().getName(),
                labelled("Mục tiêu", request.getLearningGoals()),
                labelled("Tin đăng", sentences(request.getTitle(), request.getDescription(), request.getNote()))));
        lines.addAll(studentLines(request.getStudent()));
        return finish(lines);
    }

    /** Profile-only text, used when a searcher gives no free-text criteria. */
    public String forTutor(Tutor tutor) {
        return finish(tutorLines(tutor));
    }

    public String forStudent(Student student) {
        return finish(studentLines(student));
    }

    public String hash(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private List<String> tutorLines(Tutor tutor) {
        if (tutor == null) {
            return List.of();
        }
        return List.of(
                sentences(labelled("Giới thiệu", tutor.getIntroduction()), labelled("Học vấn", tutor.getEducation())),
                sentences(labelled("Phương pháp", tutor.getTeachingMethodology()),
                        labelled("Thế mạnh", tutor.getStrengthSubjects())),
                sentences(labelled("Đối tượng", tutor.getTargetStudentType()),
                        labelled("Phong cách", tutor.getTeachingStyleTags())));
    }

    private List<String> studentLines(Student student) {
        if (student == null) {
            return List.of();
        }
        return List.of(
                sentences(labelled("Học sinh", student.getGoalsDescription()),
                        labelled("Điểm yếu", student.getWeakPoints()),
                        labelled("Trình độ", student.getCurrentLevel())),
                labelled("Phong cách mong muốn",
                        join("; ", student.getLearningStyle(), student.getPersonalityTags())));
    }

    private String finish(List<String> lines) {
        String text = lines.stream()
                .filter(line -> line != null && !line.isEmpty())
                .collect(Collectors.joining("\n"));
        return text.length() <= MAX_CHARS ? text : text.substring(0, MAX_CHARS).stripTrailing();
    }

    private String labelled(String label, String value) {
        String cleaned = clean(value);
        return cleaned.isEmpty() ? "" : label + ": " + cleaned;
    }

    /** Joins non-empty parts as sentences: "A. B. C". */
    private String sentences(String... parts) {
        return join(". ", parts);
    }

    private String join(String separator, String... parts) {
        List<String> kept = new ArrayList<>();
        for (String part : parts) {
            String cleaned = clean(part);
            if (!cleaned.isEmpty()) {
                kept.add(cleaned.endsWith(".") && separator.startsWith(".")
                        ? cleaned.substring(0, cleaned.length() - 1) : cleaned);
            }
        }
        return String.join(separator, kept);
    }

    /** Removes e-mails and phone numbers and collapses whitespace; safe to log or send to a model. */
    public String scrub(String value) {
        return clean(value);
    }

    private String clean(String value) {
        if (value == null) {
            return "";
        }
        String scrubbed = PHONE.matcher(EMAIL.matcher(value).replaceAll(" ")).replaceAll(" ");
        return WHITESPACE.matcher(scrubbed).replaceAll(" ").strip();
    }
}

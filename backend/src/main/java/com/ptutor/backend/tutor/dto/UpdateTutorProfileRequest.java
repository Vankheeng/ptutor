package com.ptutor.backend.tutor.dto;

import java.time.LocalDate;
import java.util.UUID;

import com.ptutor.backend.entity.enums.Gender;

import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record UpdateTutorProfileRequest(
        @Size(max = 100) @Pattern(regexp = ".*\\S.*", message = "First name must not be blank") String firstName,
        @Size(max = 100) @Pattern(regexp = ".*\\S.*", message = "Last name must not be blank") String lastName,
        @Size(max = 20) @Pattern(regexp = ".*\\S.*", message = "Phone must not be blank") String phone,
        Gender gender,
        @PastOrPresent LocalDate dateOfBirth,
        @Size(max = 500) @Pattern(regexp = ".*\\S.*", message = "Avatar URL must not be blank") String avatarUrl,
        @Size(max = 255) @Pattern(regexp = ".*\\S.*", message = "Detail address must not be blank") String detailAddress,
        UUID provinceId,
        UUID districtId,
        @Pattern(regexp = ".*\\S.*", message = "Introduction must not be blank") String introduction,
        @PositiveOrZero(message = "Experience years must not be negative") Integer experienceYears,
        @Pattern(regexp = ".*\\S.*", message = "Education must not be blank") String education,
        @Size(max = 255) @Pattern(regexp = ".*\\S.*", message = "Teaching style tags must not be blank") String teachingStyleTags,
        @Pattern(regexp = ".*\\S.*", message = "Teaching methodology must not be blank") String teachingMethodology,
        @Pattern(regexp = ".*\\S.*", message = "Strength subjects must not be blank") String strengthSubjects,
        @Size(max = 255) @Pattern(regexp = ".*\\S.*", message = "Target student type must not be blank") String targetStudentType) {

    public boolean hasAnyUpdate() {
        return firstName != null
                || lastName != null
                || phone != null
                || gender != null
                || dateOfBirth != null
                || avatarUrl != null
                || detailAddress != null
                || provinceId != null
                || districtId != null
                || introduction != null
                || experienceYears != null
                || education != null
                || teachingStyleTags != null
                || teachingMethodology != null
                || strengthSubjects != null
                || targetStudentType != null;
    }
}

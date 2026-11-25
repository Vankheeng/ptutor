package com.ptutor.backend.dto.request;

import java.time.LocalDate;
import java.util.UUID;

import com.ptutor.backend.entity.enums.Gender;

import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateStudentProfileRequest(
        @Size(max = 100) @Pattern(regexp = ".*\\S.*", message = "First name must not be blank") String firstName,
        @Size(max = 100) @Pattern(regexp = ".*\\S.*", message = "Last name must not be blank") String lastName,
        @Size(max = 20) @Pattern(regexp = ".*\\S.*", message = "Phone must not be blank") String phone,
        Gender gender,
        @PastOrPresent LocalDate dateOfBirth,
        @Size(max = 500) String avatarUrl,
        @Size(max = 255) String detailAddress,
        UUID provinceId,
        UUID districtId,
        String introduction,
        @Size(max = 50) String learningStyle,
        @Size(max = 255) String personalityTags,
        String goalsDescription,
        @Size(max = 50) String currentLevel,
        String weakPoints) {

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
                || learningStyle != null
                || personalityTags != null
                || goalsDescription != null
                || currentLevel != null
                || weakPoints != null;
    }
}

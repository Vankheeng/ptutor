package com.ptutor.backend.dto.response;

import java.util.UUID;

/** Information a tutor may see without exposing the student's contact details. */
public record PublicStudentProfileResponse(
        UUID studentId,
        String firstName,
        String lastName,
        String avatarUrl,
        String districtName,
        String provinceName,
        String introduction,
        String learningStyle,
        String personalityTags,
        String goalsDescription,
        String currentLevel,
        String weakPoints) {
}

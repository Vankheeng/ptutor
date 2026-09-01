package com.ptutor.backend.dto.response;

import java.time.LocalDate;
import java.util.UUID;

import com.ptutor.backend.entity.enums.Gender;

public record StudentProfileResponse(
        UUID userId,
        UUID studentId,
        String email,
        String firstName,
        String lastName,
        String phone,
        Gender gender,
        LocalDate dateOfBirth,
        String avatarUrl,
        AddressResponse address,
        String introduction,
        String learningStyle,
        String personalityTags,
        String goalsDescription,
        String currentLevel,
        String weakPoints) {
}

package com.ptutor.backend.dto.response;

import java.util.UUID;

import com.ptutor.backend.dto.enums.UserRole;

public record RegisterResponse(UUID userId, String email, UserRole role) {
}

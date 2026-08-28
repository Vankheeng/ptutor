package com.ptutor.backend.auth.dto;

import java.util.UUID;

public record RegisterResponse(UUID userId, String email, UserRole role) {
}

package com.ptutor.backend.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record WalletSecurityChallengeResponse(UUID challengeId, String maskedAccountNumber, LocalDateTime expiresAt) {}

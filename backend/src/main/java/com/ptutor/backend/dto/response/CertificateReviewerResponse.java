package com.ptutor.backend.dto.response;

import java.util.UUID;

public record CertificateReviewerResponse(
        UUID id,
        String name) {
}

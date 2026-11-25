package com.ptutor.backend.dto.response;

public record RefundEvidenceUploadResponse(
        String fileUrl,
        String previewUrl,
        String fileType) {
}

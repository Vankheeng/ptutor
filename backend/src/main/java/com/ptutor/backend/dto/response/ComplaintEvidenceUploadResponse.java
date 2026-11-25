package com.ptutor.backend.dto.response;

public record ComplaintEvidenceUploadResponse(
        String fileUrl,
        String previewUrl,
        String fileType) {
}

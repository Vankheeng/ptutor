package com.ptutor.backend.dto.response;

public record AdminTutorTeachingHistoryResponse(
        LessonSummary summary,
        PageResponse<AdminTutorLessonResponse> lessons) {

    public record LessonSummary(
            long total,
            long scheduled,
            long pendingConfirmation,
            long confirmed,
            long completed,
            long cancelled) {
    }
}

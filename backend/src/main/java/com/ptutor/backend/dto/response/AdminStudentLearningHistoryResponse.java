package com.ptutor.backend.dto.response;

public record AdminStudentLearningHistoryResponse(
        LessonSummary summary,
        PageResponse<AdminStudentLessonResponse> lessons) {

    public record LessonSummary(
            long total,
            long scheduled,
            long pendingConfirmation,
            long confirmed,
            long completed,
            long cancelled) {
    }
}

package com.ptutor.backend.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Work an employee must hand over before their access or job function can change. */
public record EmployeeOpenAssignmentsResponse(
        List<OpenComplaint> complaints,
        List<OpenRefundRequest> refundRequests) {

    public record OpenComplaint(
            UUID id,
            String title,
            String complainantName,
            String status,
            LocalDateTime createdAt) {
    }

    public record OpenRefundRequest(
            UUID id,
            String studentName,
            String subjectName,
            LocalDateTime createdAt) {
    }
}

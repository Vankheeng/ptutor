package com.ptutor.backend.dto.response;

import java.util.List;

public record ScheduleConflictCheckResponse(boolean hasConflict, List<String> messages) {
}

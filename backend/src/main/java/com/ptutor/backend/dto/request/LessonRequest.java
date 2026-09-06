package com.ptutor.backend.dto.request;

import java.time.LocalDate;
import java.time.LocalTime;

import com.ptutor.backend.entity.enums.LessonStatus;

import jakarta.validation.constraints.Size;

/** Request body shared by lesson create, update, and status operations. */
public record LessonRequest(
        @Size(max = 255, message = "Title must not exceed 255 characters")
        String title,

        LocalDate date,

        LocalTime startTime,

        LocalTime endTime,

        @Size(max = 10000, message = "Note must not exceed 10000 characters")
        String note,

        LessonStatus status) {
}

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

        LessonStatus status,

        @Size(max = 2048, message = "Meeting link must not exceed 2048 characters")
        String meetingLink) {

    public LessonRequest(
            String title,
            LocalDate date,
            LocalTime startTime,
            LocalTime endTime,
            String note,
            LessonStatus status) {
        this(title, date, startTime, endTime, note, status, null);
    }
}

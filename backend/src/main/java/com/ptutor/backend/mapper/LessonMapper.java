package com.ptutor.backend.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.LocalDateTime;

import com.ptutor.backend.dto.response.LessonResponse;
import com.ptutor.backend.dto.response.LessonResponse.RefundSummary;
import com.ptutor.backend.entity.Lesson;

@Mapper(componentModel = "spring")
public interface LessonMapper {

    @Mapping(target = "contractId", source = "lesson.contract.id")
    @Mapping(target = "paidAt", ignore = true)
    @Mapping(target = "refund", ignore = true)
    LessonResponse toResponse(Lesson lesson);

    default LessonResponse toResponse(Lesson lesson, LocalDateTime paidAt, RefundSummary refund) {
        LessonResponse response = toResponse(lesson);
        return new LessonResponse(
                response.id(), response.contractId(), response.title(), response.date(),
                response.startTime(), response.endTime(), response.teachingMode(), response.meetingLink(), response.status(),
                response.note(), response.markedTaughtAt(), response.createdAt(), response.updatedAt(), paidAt, refund);
    }
}

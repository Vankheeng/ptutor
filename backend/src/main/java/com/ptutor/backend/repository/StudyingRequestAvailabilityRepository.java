package com.ptutor.backend.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ptutor.backend.entity.StudyingRequestAvailability;

public interface StudyingRequestAvailabilityRepository
        extends JpaRepository<StudyingRequestAvailability, UUID> {

    List<StudyingRequestAvailability> findAllByStudyingRequest_IdOrderByDayOfWeekAscStartTimeAsc(
            UUID studyingRequestId);

    List<StudyingRequestAvailability> findAllByStudyingRequest_IdInOrderByStudyingRequest_IdAscDayOfWeekAscStartTimeAsc(
            List<UUID> studyingRequestIds);
}

package com.ptutor.backend.tutor.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ptutor.backend.entity.TeachingRequestAvailability;

public interface TeachingRequestAvailabilityRepository extends JpaRepository<TeachingRequestAvailability, UUID> {

    List<TeachingRequestAvailability> findAllByTeachingRequest_IdOrderByDayOfWeekAscStartTimeAsc(UUID requestId);
}

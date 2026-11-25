package com.ptutor.backend.repository;

import java.util.UUID;

public interface TeachingRequestStudentRequestCount {

    UUID getTeachingRequestId();

    long getStudentRequestCount();

    long getPendingStudentRequestCount();
}

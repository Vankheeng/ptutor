package com.ptutor.backend.auth.dto;

public enum RegistrationRole {
    STUDENT,
    TUTOR;

    public UserRole toUserRole() {
        return this == STUDENT ? UserRole.STUDENT : UserRole.TUTOR;
    }
}

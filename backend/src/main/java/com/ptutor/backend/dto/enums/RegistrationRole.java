package com.ptutor.backend.dto.enums;

public enum RegistrationRole {
    STUDENT,
    TUTOR;

    public UserRole toUserRole() {
        return this == STUDENT ? UserRole.STUDENT : UserRole.TUTOR;
    }
}

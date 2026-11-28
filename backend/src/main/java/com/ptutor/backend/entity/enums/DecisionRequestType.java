package com.ptutor.backend.entity.enums;

/** The table of the application a willingness snapshot belongs to. */
public enum DecisionRequestType {
    /** A student applied to a teaching posting ({@code student_tutor_requests}). */
    STUDENT_TUTOR_REQUEST,
    /** A tutor proposed to a studying posting ({@code tutor_student_requests}). */
    TUTOR_STUDENT_REQUEST
}

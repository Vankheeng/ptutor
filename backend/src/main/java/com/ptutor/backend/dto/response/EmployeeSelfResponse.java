package com.ptutor.backend.dto.response;

import java.util.UUID;

import com.ptutor.backend.entity.enums.EmployeeJobFunction;
import com.ptutor.backend.entity.enums.EmployeeRole;

/** The signed-in staff member's own role and job function, so the admin UI shows only the modules they may open. */
public record EmployeeSelfResponse(
        UUID employeeId,
        EmployeeRole role,
        EmployeeJobFunction jobFunction,
        String firstName,
        String lastName) {
}

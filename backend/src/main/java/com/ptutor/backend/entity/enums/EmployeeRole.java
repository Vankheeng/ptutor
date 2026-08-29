package com.ptutor.backend.entity.enums;

public enum EmployeeRole {
    ADMIN(1),
    EMPLOYEE(2);

    private final int code;

    EmployeeRole(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static EmployeeRole fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (EmployeeRole role : values()) {
            if (role.code == code) {
                return role;
            }
        }
        throw new IllegalArgumentException("Unsupported employee role code: " + code);
    }
}

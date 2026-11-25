package com.ptutor.backend.entity.converter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.ptutor.backend.entity.enums.EmployeeRole;

class EmployeeRoleConverterTest {

    private final EmployeeRoleConverter converter = new EmployeeRoleConverter();

    @Test
    void convertsEmployeeRolesWithoutChangingExistingDatabaseCodes() {
        assertThat(converter.convertToDatabaseColumn(EmployeeRole.ADMIN)).isEqualTo(1);
        assertThat(converter.convertToDatabaseColumn(EmployeeRole.EMPLOYEE)).isEqualTo(2);
        assertThat(converter.convertToEntityAttribute(1)).isEqualTo(EmployeeRole.ADMIN);
        assertThat(converter.convertToEntityAttribute(2)).isEqualTo(EmployeeRole.EMPLOYEE);
    }
}

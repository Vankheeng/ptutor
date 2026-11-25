package com.ptutor.backend.entity.converter;

import com.ptutor.backend.entity.enums.EmployeeRole;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class EmployeeRoleConverter implements AttributeConverter<EmployeeRole, Integer> {

    @Override
    public Integer convertToDatabaseColumn(EmployeeRole role) {
        return role == null ? null : role.getCode();
    }

    @Override
    public EmployeeRole convertToEntityAttribute(Integer code) {
        return EmployeeRole.fromCode(code);
    }
}

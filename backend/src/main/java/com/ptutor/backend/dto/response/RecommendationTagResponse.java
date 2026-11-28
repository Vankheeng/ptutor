package com.ptutor.backend.dto.response;

import java.util.UUID;

import com.ptutor.backend.entity.enums.RecommendationTagGroup;
import com.ptutor.backend.entity.enums.RecommendationTagStatus;

public record RecommendationTagResponse(
        UUID id,
        RecommendationTagGroup group,
        UUID subjectId,
        String subjectName,
        String code,
        String name,
        String description,
        UUID parentId,
        String parentCode,
        RecommendationTagStatus status) {
}

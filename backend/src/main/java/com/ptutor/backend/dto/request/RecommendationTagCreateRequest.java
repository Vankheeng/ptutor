package com.ptutor.backend.dto.request;

import java.util.List;
import java.util.UUID;

import com.ptutor.backend.entity.enums.RecommendationTagGroup;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** One or several tags typed by an administrator, or picked from the LLM suggestions. */
public record RecommendationTagCreateRequest(
        @NotEmpty(message = "At least one tag is required")
        @Size(max = 30, message = "At most 30 tags can be created at once")
        List<@Valid @NotNull Item> tags) {

    public record Item(
            @NotNull(message = "Group is required") RecommendationTagGroup group,
            UUID subjectId,
            @NotBlank(message = "Code is required")
            @Pattern(regexp = "^[a-z0-9_]{3,40}$",
                    message = "Code must be 3-40 characters of lowercase letters, digits and underscores")
            String code,
            @NotBlank(message = "Name is required")
            @Size(max = 120, message = "Name must not exceed 120 characters") String name,
            @Size(max = 500, message = "Description must not exceed 500 characters") String description,
            /** Code of a top-level tag of the same group and subject, existing or earlier in this request. */
            String parentCode) {
    }
}

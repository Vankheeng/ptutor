package com.ptutor.backend.dto.response;

import java.util.List;
import java.util.UUID;

/** Topic ideas from the LLM. Nothing is stored until an administrator creates the ones they keep. */
public record RecommendationTagSuggestionResponse(
        UUID subjectId,
        String subjectName,
        String model,
        List<Suggestion> suggestions) {

    public record Suggestion(String code, String name, String description, String parentCode) {
    }
}

package com.ptutor.backend.dto.response;

import tools.jackson.databind.JsonNode;

/**
 * One way to loosen the search that would bring more good results (design 07, 7.1).
 *
 * @param gain  how many more people would score well
 * @param apply the filter values to overwrite for a one-click retry
 */
public record RelaxationSuggestion(String code, String message, int gain, JsonNode apply) {
}

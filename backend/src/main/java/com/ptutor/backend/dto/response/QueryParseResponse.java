package com.ptutor.backend.dto.response;

import java.util.List;

import tools.jackson.databind.JsonNode;

/**
 * What a typed sentence means in terms of the filter form (design 08, 8.2).
 *
 * @param filters      only the fields that were recognised, in the shape of the search request's "filters"
 * @param semanticText the part of the sentence that fits no filter; goes into the search as free text
 * @param notes        things the user should know: what was not recognised, what was chosen for them
 * @param understood   false when the sentence could not be analysed and is used as free text as a whole
 */
public record QueryParseResponse(JsonNode filters, String semanticText, List<String> notes, boolean understood) {
}

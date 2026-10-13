package com.ptutor.backend.dto.recommendation;

/**
 * Where one posting lands when a given search is run (design 07, 7.3).
 *
 * @param reason     the first reason it fits, or null when there is nothing specific to say
 * @param personName who posted it, when that may be shown to the searcher; otherwise null
 */
public record MatchPosition(int rank, double score, String reason, String title, String personName) {
}

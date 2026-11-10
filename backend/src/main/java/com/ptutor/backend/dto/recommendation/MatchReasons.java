package com.ptutor.backend.dto.recommendation;

import java.util.List;

/** Why a result suits the searcher: at most three positive sentences and one caution (design 06, 6.13). */
public record MatchReasons(List<String> reasons, String caution) {

    public static final MatchReasons NONE = new MatchReasons(List.of(), null);
}

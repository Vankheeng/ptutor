package com.ptutor.backend.entity.enums;

/** How a search gets its rerank scores ({@code app.recommendation.rerank.mode}, design 13, 13.2). */
public enum RerankMode {
    /** The search waits for the model within the time budget (the design until 26/10/2026). */
    ONLINE,
    /** The search uses cached scores only; missing pairs are scored in the background for later searches. */
    ASYNC,
    OFF
}

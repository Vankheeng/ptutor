package com.ptutor.backend.entity.enums;

/** Kind of a willingness snapshot (design 12, section 12.6). */
public enum DecisionSampleKind {
    /** The other side has to accept or reject: labelled by that decision later. */
    DECISION,
    /** The sender reached out on their own: a positive sample of the sender's willingness. */
    INITIATIVE
}

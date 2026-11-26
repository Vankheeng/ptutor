package com.ptutor.backend.entity.enums;

/** Nơi người dùng bấm gửi (14 mục 14.4): trợ lý từ chối, trả lời một phần, sau khi bấm 👎, hoặc từ menu widget. */
public enum SupportRequestSource {
    ASSISTANT_ABSTAIN,
    ASSISTANT_PARTIAL,
    ASSISTANT_FEEDBACK,
    MENU
}

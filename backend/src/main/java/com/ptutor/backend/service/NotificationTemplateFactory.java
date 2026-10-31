package com.ptutor.backend.service;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.ptutor.backend.entity.enums.NotificationEventType;
import com.ptutor.backend.entity.enums.NotificationType;
import com.ptutor.backend.event.NotificationDomainEvent;

@Component
class NotificationTemplateFactory {

    NotificationContent create(NotificationDomainEvent event) {
        Map<String, String> data = event.data();
        String name = data.getOrDefault("name", "");
        String reason = data.getOrDefault("reason", "");
        String amount = data.getOrDefault("amount", "");

        return switch (event.eventType()) {
            case PAYMENT_SUCCEEDED -> content(NotificationType.PAYMENT,
                    "Payment successful", "Your payment has been completed successfully.");
            case PAYMENT_INSTALLMENT_DUE -> content(NotificationType.PAYMENT,
                    "Tuition payment reminder", "A tuition installment is due today.");
            case TUTOR_REQUEST_RECEIVED -> content(NotificationType.REQUEST,
                    "New tutor proposal", "A tutor sent a teaching proposal for your studying request.");
            case TUTOR_REQUEST_ACCEPTED -> content(NotificationType.REQUEST,
                    "Tutor proposal accepted", "Your tutor proposal was accepted by the student.");
            case TUTOR_REQUEST_REJECTED -> content(NotificationType.REQUEST,
                    "Tutor proposal rejected", "Your tutor proposal was rejected by the student.");
            case STUDENT_REQUEST_RECEIVED -> content(NotificationType.REQUEST,
                    "New student application", "A student sent an application for your teaching request.");
            case STUDENT_REQUEST_ACCEPTED -> content(NotificationType.REQUEST,
                    "Student application accepted", "Your student application was accepted by the tutor.");
            case STUDENT_REQUEST_REJECTED -> content(NotificationType.REQUEST,
                    "Student application rejected", "Your student application was rejected by the tutor.");
            case CONTRACT_SIGNATURE_REQUIRED -> content(NotificationType.CONTRACT,
                    "Contract signature required", "A contract is waiting for your signature.");
            case CONTRACT_SIGNED -> content(NotificationType.CONTRACT,
                    "Contract signed", "The other participant signed the contract.");
            case CONTRACT_REJECTED -> content(NotificationType.CONTRACT,
                    "Contract rejected", "The contract was rejected by the other participant.");
            case CONTRACT_CANCELLED -> content(NotificationType.CONTRACT,
                    "Contract cancelled", "A pending contract was cancelled by its creator.");
            case CONTRACT_RENEWAL_PROPOSED -> content(NotificationType.CONTRACT,
                    "Contract renewal proposed", "A contract renewal is waiting for your signature.");
            case CERTIFICATE_REVIEW_REQUIRED -> content(NotificationType.SYSTEM,
                    "Certificate review required",
                    "A certificate \"" + name + "\" is waiting for approval.");
            case CERTIFICATE_APPROVED -> content(NotificationType.SYSTEM,
                    "Certificate approved", "Your certificate \"" + name + "\" has been approved.");
            case CERTIFICATE_REJECTED -> content(NotificationType.SYSTEM,
                    "Certificate rejected", "Your certificate \"" + name + "\" was rejected. Reason: " + reason);
            case TUTOR_PROFILE_APPROVED -> content(NotificationType.SYSTEM,
                    "Tutor profile approved",
                    "Your tutor profile has been approved. Students can now find you in recommendations.");
            case TUTOR_PROFILE_REJECTED -> content(NotificationType.SYSTEM,
                    "Tutor profile rejected", "Your tutor profile was rejected. Reason: " + sentence(reason)
                            + " Update your teaching profile to submit it for review again.");
            case TEACHING_REQUEST_APPROVED -> content(NotificationType.SYSTEM,
                    "Teaching request approved", "Your teaching request \"" + name + "\" has been approved.");
            case TEACHING_REQUEST_REJECTED -> content(NotificationType.SYSTEM,
                    "Teaching request rejected", "Your teaching request \"" + name
                            + "\" was rejected. Reason: " + reason
                            + ". The posting fee of " + data.getOrDefault("amount", "")
                            + " has been refunded to your wallet balance.");
            case STUDYING_REQUEST_REVIEW_REQUIRED -> content(NotificationType.SYSTEM,
                    "Studying request review required",
                    "A studying request \"" + name + "\" is waiting for approval.");
            case STUDYING_REQUEST_APPROVED -> content(NotificationType.SYSTEM,
                    "Studying request approved", "Your studying request \"" + name + "\" has been approved.");
            case STUDYING_REQUEST_REJECTED -> content(NotificationType.SYSTEM,
                    "Studying request rejected", "Your studying request \"" + name
                            + "\" was rejected. Reason: " + reason
                            + ". The posting fee of " + data.getOrDefault("amount", "")
                            + " has been refunded to your wallet balance.");
            case COMPLAINT_EVIDENCE_REQUESTED -> content(NotificationType.COMPLAINT,
                    "Additional evidence required", "Additional evidence is required for your complaint. " + reason);
            case COMPLAINT_RESOLVED -> content(NotificationType.COMPLAINT,
                    "Complaint accepted", "Your complaint has been accepted. Resolution: " + reason);
            case COMPLAINT_REJECTED -> content(NotificationType.COMPLAINT,
                    "Complaint rejected", "Your complaint has been rejected. Reason: " + reason);
            case COMPLAINT_RECEIVED_AGAINST_YOU -> content(NotificationType.COMPLAINT,
                    "Complaint received about you",
                    "A complaint about your contract is now under review. You can submit one response with evidence.");
            case COMPLAINT_AGAINST_YOU_RESOLVED -> content(NotificationType.COMPLAINT,
                    "Complaint about you resolved",
                    "A complaint about you has been resolved. Party at fault: "
                            + data.getOrDefault("faultParty", "") + ". Resolution: " + reason);
            case COMPLAINT_AGAINST_YOU_REJECTED -> content(NotificationType.COMPLAINT,
                    "Complaint about you rejected", "A complaint about you has been rejected. Reason: " + reason);
            case ACCOUNT_SUSPENDED -> {
                String until = data.getOrDefault("until", "");
                String duration = "TEMPORARY".equals(data.get("type"))
                        ? " until " + until
                        : " permanently";
                yield content(NotificationType.SYSTEM, "Account suspended",
                        "Your account has been suspended" + duration + ". Reason: " + reason);
            }
            case ACCOUNT_REACTIVATED -> content(NotificationType.SYSTEM,
                    "Account reactivated", "Your account has been reactivated.");
            case TEACHING_REQUEST_REVIEW_REQUIRED -> content(NotificationType.REQUEST,
                    "Teaching request review required",
                    "A teaching request \"" + name + "\" is waiting for approval.");
            case LESSON_MARKED_TAUGHT -> content(NotificationType.CONTRACT,
                    "Lesson awaiting confirmation",
                    "Your tutor marked lesson \"" + name + "\" as taught. Please confirm it.");
            case LESSON_CANCELLED -> content(NotificationType.CONTRACT,
                    "Lesson cancelled", "Your tutor cancelled lesson \"" + name + "\".");
            case WITHDRAWAL_REVIEW_REQUIRED -> content(NotificationType.PAYMENT,
                    "Withdrawal review required",
                    "A withdrawal request of " + amount + " VND is waiting for approval.");
            case REFUND_REQUEST_REVIEW_REQUIRED -> content(NotificationType.PAYMENT,
                    "Refund review required", "A student refund request is waiting for review.");
            case REFUND_REQUEST_SUBMITTED -> content(NotificationType.PAYMENT,
                    "Student requested a refund",
                    "A student requested a refund on your contract. Respond before "
                            + data.getOrDefault("deadline", "") + ".");
            case REFUND_REQUEST_TUTOR_RESPONDED -> content(NotificationType.PAYMENT,
                    "Tutor responded to a refund request", "The tutor has responded to a refund request you are reviewing.");
            case REFUND_REQUEST_APPROVED -> content(NotificationType.PAYMENT,
                    "Refund approved", amount + " VND has been refunded to the student's wallet balance.");
            case REFUND_REQUEST_REJECTED -> content(NotificationType.PAYMENT,
                    "Refund rejected", "The refund request was rejected. Reason: " + reason);
        };
    }

    /** Reviewers often end their reason with a full stop; avoid doubling it. */
    private String sentence(String text) {
        String value = text.strip();
        return value.isEmpty() || value.matches(".*[.!?]$") ? value : value + ".";
    }

    private NotificationContent content(NotificationType type, String title, String text) {
        return new NotificationContent(type, title, text);
    }
}

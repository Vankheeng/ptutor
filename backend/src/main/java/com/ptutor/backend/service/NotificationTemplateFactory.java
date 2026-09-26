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

        return switch (event.eventType()) {
            case PAYMENT_SUCCEEDED -> content(NotificationType.PAYMENT,
                    "Payment successful", "Your payment has been completed successfully.");
            case PAYMENT_INSTALLMENT_DUE -> content(NotificationType.PAYMENT,
                    "Tuition payment reminder", "A tuition installment is due in 3 days.");
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
            case CERTIFICATE_APPROVED -> content(NotificationType.SYSTEM,
                    "Certificate approved", "Your certificate \"" + name + "\" has been approved.");
            case CERTIFICATE_REJECTED -> content(NotificationType.SYSTEM,
                    "Certificate rejected", "Your certificate \"" + name + "\" was rejected. Reason: " + reason);
            case TEACHING_REQUEST_APPROVED -> content(NotificationType.SYSTEM,
                    "Teaching request approved", "Your teaching request \"" + name + "\" has been approved.");
            case TEACHING_REQUEST_REJECTED -> content(NotificationType.SYSTEM,
                    "Teaching request rejected", "Your teaching request \"" + name
                            + "\" was rejected. Reason: " + reason
                            + ". The posting fee of " + data.getOrDefault("amount", "")
                            + " has been refunded to your wallet balance.");
            case COMPLAINT_EVIDENCE_REQUESTED -> content(NotificationType.COMPLAINT,
                    "Additional evidence required", "Additional evidence is required for your complaint. " + reason);
            case COMPLAINT_RESOLVED -> content(NotificationType.COMPLAINT,
                    "Complaint accepted", "Your complaint has been accepted. Resolution: " + reason);
            case COMPLAINT_REJECTED -> content(NotificationType.COMPLAINT,
                    "Complaint rejected", "Your complaint has been rejected. Reason: " + reason);
        };
    }

    private NotificationContent content(NotificationType type, String title, String text) {
        return new NotificationContent(type, title, text);
    }
}

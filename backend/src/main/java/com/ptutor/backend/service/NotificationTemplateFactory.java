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
                    "Thanh toán thành công", "Thanh toán của bạn đã được hoàn tất thành công.");
            case PAYMENT_INSTALLMENT_DUE -> content(NotificationType.PAYMENT,
                    "Nhắc thanh toán học phí", "Một kỳ học phí đến hạn thanh toán hôm nay.");
            case TUTOR_REQUEST_RECEIVED -> content(NotificationType.REQUEST,
                    "Đề nghị gia sư mới", "Một gia sư đã gửi đề nghị dạy cho yêu cầu học của bạn.");
            case TUTOR_REQUEST_ACCEPTED -> content(NotificationType.REQUEST,
                    "Đề nghị gia sư được chấp nhận", "Học viên đã chấp nhận đề nghị dạy của bạn.");
            case TUTOR_REQUEST_REJECTED -> content(NotificationType.REQUEST,
                    "Đề nghị gia sư bị từ chối", "Học viên đã từ chối đề nghị dạy của bạn.");
            case STUDENT_REQUEST_RECEIVED -> content(NotificationType.REQUEST,
                    "Đơn ứng tuyển học viên mới", "Một học viên đã gửi đơn ứng tuyển cho yêu cầu dạy của bạn.");
            case STUDENT_REQUEST_ACCEPTED -> content(NotificationType.REQUEST,
                    "Đơn ứng tuyển được chấp nhận", "Gia sư đã chấp nhận đơn ứng tuyển của bạn.");
            case STUDENT_REQUEST_REJECTED -> content(NotificationType.REQUEST,
                    "Đơn ứng tuyển bị từ chối", "Gia sư đã từ chối đơn ứng tuyển của bạn.");
            case CONTRACT_SIGNATURE_REQUIRED -> content(NotificationType.CONTRACT,
                    "Hợp đồng cần ký", "Có một hợp đồng đang chờ bạn ký.");
            case CONTRACT_SIGNED -> content(NotificationType.CONTRACT,
                    "Hợp đồng đã được ký", "Người tham gia còn lại đã ký hợp đồng.");
            case CONTRACT_REJECTED -> content(NotificationType.CONTRACT,
                    "Hợp đồng bị từ chối", "Người tham gia còn lại đã từ chối hợp đồng.");
            case CONTRACT_CANCELLED -> content(NotificationType.CONTRACT,
                    "Hợp đồng đã bị hủy", "Hợp đồng đang chờ đã bị người tạo hủy.");
            case CONTRACT_STOP_REQUESTED -> content(NotificationType.CONTRACT,
                    "Yêu cầu dừng hợp đồng",
                    "Gia sư đã gửi yêu cầu dừng hợp đồng. Vui lòng xem và xử lý yêu cầu.");
            case CONTRACT_RENEWAL_PROPOSED -> content(NotificationType.CONTRACT,
                    "Đề nghị gia hạn hợp đồng", "Một đề nghị gia hạn hợp đồng đang chờ bạn ký.");
            case CERTIFICATE_REVIEW_REQUIRED -> content(NotificationType.SYSTEM,
                    "Chứng chỉ cần được duyệt",
                    "Chứng chỉ \"" + name + "\" đang chờ được duyệt.");
            case CERTIFICATE_APPROVED -> content(NotificationType.SYSTEM,
                    "Chứng chỉ đã được duyệt", "Chứng chỉ \"" + name + "\" của bạn đã được duyệt.");
            case CERTIFICATE_REJECTED -> content(NotificationType.SYSTEM,
                    "Chứng chỉ bị từ chối", "Chứng chỉ \"" + name + "\" của bạn bị từ chối. Lý do: " + reason);
            case TUTOR_PROFILE_APPROVED -> content(NotificationType.SYSTEM,
                    "Hồ sơ gia sư đã được duyệt",
                    "Hồ sơ gia sư của bạn đã được duyệt. Học viên có thể tìm thấy bạn trong danh sách đề xuất.");
            case TUTOR_PROFILE_REJECTED -> content(NotificationType.SYSTEM,
                    "Hồ sơ gia sư bị từ chối", "Hồ sơ gia sư của bạn bị từ chối. Lý do: " + sentence(reason)
                            + " Hãy cập nhật hồ sơ giảng dạy và gửi lại để được duyệt.");
            case TEACHING_REQUEST_APPROVED -> content(NotificationType.SYSTEM,
                    "Yêu cầu dạy đã được duyệt", "Yêu cầu dạy \"" + name + "\" của bạn đã được duyệt.");
            case TEACHING_REQUEST_REJECTED -> content(NotificationType.SYSTEM,
                    "Yêu cầu dạy bị từ chối", "Yêu cầu dạy \"" + name
                            + "\" bị từ chối. Lý do: " + reason
                            + ". Phí đăng tin " + data.getOrDefault("amount", "")
                            + " đã được hoàn vào số dư ví của bạn.");
            case STUDYING_REQUEST_REVIEW_REQUIRED -> content(NotificationType.SYSTEM,
                    "Yêu cầu học cần được duyệt",
                    "Yêu cầu học \"" + name + "\" đang chờ được duyệt.");
            case STUDYING_REQUEST_APPROVED -> content(NotificationType.SYSTEM,
                    "Yêu cầu học đã được duyệt", "Yêu cầu học \"" + name + "\" của bạn đã được duyệt.");
            case STUDYING_REQUEST_REJECTED -> content(NotificationType.SYSTEM,
                    "Yêu cầu học bị từ chối", "Yêu cầu học \"" + name
                            + "\" bị từ chối. Lý do: " + reason
                            + ". Phí đăng tin " + data.getOrDefault("amount", "")
                            + " đã được hoàn vào số dư ví của bạn.");
            case COMPLAINT_EVIDENCE_REQUESTED -> content(NotificationType.COMPLAINT,
                    "Cần bổ sung bằng chứng", "Khiếu nại của bạn cần bổ sung bằng chứng. " + reason);
            case COMPLAINT_RESOLVED -> content(NotificationType.COMPLAINT,
                    "Khiếu nại được chấp nhận", "Khiếu nại của bạn đã được chấp nhận. Kết quả: " + reason);
            case COMPLAINT_REJECTED -> content(NotificationType.COMPLAINT,
                    "Khiếu nại bị từ chối", "Khiếu nại của bạn bị từ chối. Lý do: " + reason);
            case COMPLAINT_RECEIVED_AGAINST_YOU -> content(NotificationType.COMPLAINT,
                    "Có khiếu nại về bạn",
                    "Khiếu nại liên quan đến hợp đồng của bạn đang được xem xét. Bạn có thể gửi một lần phản hồi kèm bằng chứng.");
            case COMPLAINT_AGAINST_YOU_RESOLVED -> content(NotificationType.COMPLAINT,
                    "Khiếu nại về bạn đã được giải quyết",
                    "Khiếu nại về bạn đã được giải quyết. Bên có lỗi: "
                            + data.getOrDefault("faultParty", "") + ". Kết quả: " + reason);
            case COMPLAINT_AGAINST_YOU_REJECTED -> content(NotificationType.COMPLAINT,
                    "Khiếu nại về bạn bị từ chối", "Khiếu nại về bạn bị từ chối. Lý do: " + reason);
            case ACCOUNT_SUSPENDED -> {
                String until = data.getOrDefault("until", "");
                String duration = "TEMPORARY".equals(data.get("type"))
                        ? " đến " + until
                        : " vĩnh viễn";
                yield content(NotificationType.SYSTEM, "Tài khoản bị tạm khóa",
                        "Tài khoản của bạn đã bị khóa" + duration + ". Lý do: " + reason);
            }
            case ACCOUNT_REACTIVATED -> content(NotificationType.SYSTEM,
                    "Tài khoản đã được kích hoạt lại", "Tài khoản của bạn đã được kích hoạt lại.");
            case TEACHING_REQUEST_REVIEW_REQUIRED -> content(NotificationType.REQUEST,
                    "Yêu cầu dạy cần được duyệt",
                    "Yêu cầu dạy \"" + name + "\" đang chờ được duyệt.");
            case LESSON_MARKED_TAUGHT -> content(NotificationType.CONTRACT,
                    "Buổi học chờ xác nhận",
                    "Gia sư đã đánh dấu buổi \"" + name + "\" đã dạy. Vui lòng xác nhận.");
            case LESSON_CANCELLED -> content(NotificationType.CONTRACT,
                    "Buổi học đã bị hủy", "Gia sư đã hủy buổi \"" + name + "\"."
                            + (amount.isBlank() ? "" : " " + amount + " VND đã được hoàn vào ví của bạn."));
            case LESSON_RESCHEDULED -> content(NotificationType.CONTRACT,
                    "Buổi học đã được đổi lịch",
                    "Gia sư đã đổi lịch buổi \"" + name + "\" sang "
                            + data.getOrDefault("newSchedule", "lịch mới") + ". Vui lòng kiểm tra lại lịch học.");
            case WITHDRAWAL_REVIEW_REQUIRED -> content(NotificationType.PAYMENT,
                    "Yêu cầu rút tiền cần được duyệt",
                    "Yêu cầu rút " + amount + " VND đang chờ được duyệt.");
            case WITHDRAWAL_REQUEST_CREATED -> content(NotificationType.PAYMENT,
                    "Đã tạo yêu cầu rút tiền",
                    "Yêu cầu rút " + amount + " VND đã được ghi nhận và đang chờ duyệt.");
            case WITHDRAWAL_REQUEST_CANCELLED -> content(NotificationType.PAYMENT,
                    "Đã hủy yêu cầu rút tiền",
                    "Yêu cầu rút " + amount + " VND đã được hủy. Tiền đã được cộng lại vào ví.");
            case WITHDRAWAL_REQUEST_APPROVED -> content(NotificationType.PAYMENT,
                    "Yêu cầu rút tiền đã được duyệt", "Yêu cầu rút " + amount + " VND đang chờ chuyển khoản.");
            case WITHDRAWAL_REQUEST_REJECTED -> content(NotificationType.PAYMENT,
                    "Yêu cầu rút tiền bị từ chối", "Yêu cầu rút " + amount + " VND bị từ chối. Tiền đã được trả lại ví.");
            case WITHDRAWAL_REQUEST_COMPLETED -> content(NotificationType.PAYMENT,
                    "Rút tiền hoàn tất", "Đã chuyển " + amount + " VND đến tài khoản ngân hàng của bạn.");
            case WALLET_BALANCE_CHANGED -> {
                String direction = "CREDIT".equals(data.get("transactionType")) ? "cộng vào" : "trừ khỏi";
                String balance = data.getOrDefault("balanceAfter", "0");
                yield content(NotificationType.PAYMENT,
                        "Biến động số dư",
                        amount + " VND đã được " + direction + " ví PTutor. Số dư hiện tại: " + balance + " VND.");
            }
            case REFUND_REQUEST_REVIEW_REQUIRED -> content(NotificationType.PAYMENT,
                    "Yêu cầu hoàn tiền cần được xem xét", "Một yêu cầu hoàn tiền của học viên đang chờ được xem xét.");
            case REFUND_REQUEST_SUBMITTED -> content(NotificationType.PAYMENT,
                    "Học viên yêu cầu hoàn tiền",
                    "Học viên đã yêu cầu hoàn tiền cho hợp đồng của bạn. Vui lòng phản hồi trước "
                            + data.getOrDefault("deadline", "") + ".");
            case REFUND_REQUEST_TUTOR_RESPONDED -> content(NotificationType.PAYMENT,
                    "Gia sư đã phản hồi yêu cầu hoàn tiền", "Gia sư đã phản hồi yêu cầu hoàn tiền bạn đang xem xét.");
            case REFUND_REQUEST_APPROVED -> content(NotificationType.PAYMENT,
                    "Yêu cầu hoàn tiền được duyệt", amount + " VND đã được hoàn vào số dư ví của học viên.");
            case REFUND_REQUEST_REJECTED -> content(NotificationType.PAYMENT,
                    "Yêu cầu hoàn tiền bị từ chối", "Yêu cầu hoàn tiền bị từ chối. Lý do: " + reason);
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

package com.ptutor.backend.service;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.ptutor.backend.exception.ApiException;

@Service
public class RefundFileStorageService {

    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final Map<String, String> EXTENSIONS = Map.of(
            "application/pdf", ".pdf",
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp",
            "video/mp4", ".mp4",
            "video/webm", ".webm",
            "text/plain", ".txt",
            "application/msword", ".doc",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", ".docx");

    private final S3FileStorageService storageService;

    @Autowired
    public RefundFileStorageService(S3FileStorageService storageService) {
        this.storageService = storageService;
    }

    /**
     * Separate storage boundary for refund evidence. It intentionally uses the
     * shared S3 implementation but a refund-specific object prefix and errors.
     */
    public String upload(MultipartFile file) {
        String contentType = validate(file);
        return storageService.upload(file, "refunds/", EXTENSIONS.get(contentType),
                new S3FileStorageService.UploadErrors(
                        "REFUND_STORAGE_UNAVAILABLE",
                        "Refund evidence storage is not configured",
                        "REFUND_UPLOAD_FAILED",
                        "The refund evidence file could not be read",
                        "REFUND_STORAGE_REJECTED",
                        "Object storage rejected the refund evidence upload",
                        "REFUND_STORAGE_UNREACHABLE",
                        "Refund evidence storage could not be reached"));
    }

    public String accessUrl(String reference) {
        return storageService.accessUrl(
                reference,
                "REFUND_STORAGE_UNAVAILABLE",
                "Refund evidence storage is not configured",
                "REFUND_FILE_NOT_FOUND",
                "The refund evidence file is unavailable");
    }

    private String validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REFUND_FILE_REQUIRED",
                    "Choose a refund evidence file to upload");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REFUND_FILE_TOO_LARGE",
                    "Refund evidence files must be 10 MB or smaller");
        }
        String contentType = file.getContentType();
        if (contentType == null) {
            throw unsupportedType();
        }
        contentType = contentType.toLowerCase(Locale.ROOT);
        if (!EXTENSIONS.containsKey(contentType)) {
            throw unsupportedType();
        }
        return contentType;
    }

    private ApiException unsupportedType() {
        return new ApiException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_REFUND_FILE_TYPE",
                "Refund evidence files must be PDF, image, video, TXT or Word files");
    }
}

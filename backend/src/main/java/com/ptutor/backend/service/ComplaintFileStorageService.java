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
public class ComplaintFileStorageService {

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
    public ComplaintFileStorageService(S3FileStorageService storageService) {
        this.storageService = storageService;
    }

    /**
     * Backwards-compatible constructor for unit tests and callers that provide
     * the storage settings directly.
     */
    public ComplaintFileStorageService(
            String bucket,
            String region,
            String endpoint,
            String publicEndpoint,
            String accessKey,
            String secretKey,
            Duration presignDuration) {
        this.storageService = new S3FileStorageService(
                bucket, region, endpoint, publicEndpoint, accessKey, secretKey, presignDuration);
    }

    public String upload(MultipartFile file) {
        String contentType = validate(file);
        return storageService.upload(file, "complaints/", EXTENSIONS.get(contentType),
                new S3FileStorageService.UploadErrors(
                        "COMPLAINT_STORAGE_UNAVAILABLE",
                        "Complaint file storage is not configured",
                        "COMPLAINT_UPLOAD_FAILED",
                        "The complaint evidence file could not be read",
                        "COMPLAINT_STORAGE_REJECTED",
                        "Object storage rejected the complaint evidence upload",
                        "COMPLAINT_STORAGE_UNREACHABLE",
                        "Complaint evidence storage could not be reached"));
    }

    public String accessUrl(String reference) {
        return storageService.accessUrl(
                reference,
                "COMPLAINT_STORAGE_UNAVAILABLE",
                "Complaint file storage is not configured",
                "COMPLAINT_FILE_NOT_FOUND",
                "The complaint evidence file is unavailable");
    }

    private String validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "COMPLAINT_FILE_REQUIRED",
                    "Choose a complaint evidence file to upload");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "COMPLAINT_FILE_TOO_LARGE",
                    "Complaint evidence files must be 10 MB or smaller");
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
        return new ApiException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_COMPLAINT_FILE_TYPE",
                "Complaint evidence files must be PDF, image, video, TXT or Word files");
    }
}

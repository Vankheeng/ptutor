package com.ptutor.backend.service;

import java.time.Duration;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.ptutor.backend.exception.ApiException;

@Service
public class CertificateFileStorageService {

    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;

    private final S3FileStorageService storageService;

    @Autowired
    public CertificateFileStorageService(S3FileStorageService storageService) {
        this.storageService = storageService;
    }

    /**
     * Backwards-compatible constructor for unit tests and callers that provide
     * the storage settings directly.
     */
    public CertificateFileStorageService(
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
        String extension = contentType.equals("application/pdf") ? ".pdf"
                : contentType.equals("image/png") ? ".png" : ".jpg";
        return storageService.upload(file, "certificates/", extension,
                new S3FileStorageService.UploadErrors(
                        "CERTIFICATE_STORAGE_UNAVAILABLE",
                        "Certificate file storage is not configured",
                        "CERTIFICATE_UPLOAD_FAILED",
                        "The certificate file could not be read",
                        "CERTIFICATE_STORAGE_REJECTED",
                        "Object storage rejected the certificate upload",
                        "CERTIFICATE_STORAGE_UNREACHABLE",
                        "Certificate storage could not be reached. Check the storage endpoint and credentials."));
    }

    public String accessUrl(String reference) {
        return storageService.accessUrl(
                reference,
                "CERTIFICATE_STORAGE_UNAVAILABLE",
                "Certificate file storage is not configured",
                "CERTIFICATE_FILE_NOT_FOUND",
                "The certificate file is unavailable");
    }

    private String validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CERTIFICATE_FILE_REQUIRED",
                    "Choose a certificate file to upload");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CERTIFICATE_FILE_TOO_LARGE",
                    "Certificate files must be 10 MB or smaller");
        }
        String contentType = file.getContentType();
        if (contentType == null || !(contentType.equalsIgnoreCase("application/pdf")
                || contentType.equalsIgnoreCase("image/jpeg")
                || contentType.equalsIgnoreCase("image/png"))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_CERTIFICATE_FILE_TYPE",
                    "Certificate files must be PDF, JPEG or PNG");
        }
        return contentType.toLowerCase(Locale.ROOT);
    }
}

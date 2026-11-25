package com.ptutor.backend.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import com.ptutor.backend.exception.ApiException;

class CertificateFileStorageServiceTest {

    @Test
    void uploadExplainsWhenBucketIsNotConfigured() {
        CertificateFileStorageService service = service("");
        MockMultipartFile file = file("certificate.pdf", "application/pdf", new byte[] {1, 2, 3});

        assertThatThrownBy(() -> service.upload(file))
                .isInstanceOf(ApiException.class)
                .hasMessage("Certificate file storage is not configured")
                .extracting("code")
                .isEqualTo("CERTIFICATE_STORAGE_UNAVAILABLE");
    }

    @Test
    void uploadRejectsUnsupportedFileTypeBeforeCallingStorage() {
        CertificateFileStorageService service = service("certificates");
        MockMultipartFile file = file("certificate.txt", "text/plain", new byte[] {1, 2, 3});

        assertThatThrownBy(() -> service.upload(file))
                .isInstanceOf(ApiException.class)
                .hasMessage("Certificate files must be PDF, JPEG or PNG");
    }

    @Test
    void uploadRejectsFilesLargerThanTenMegabytesBeforeCallingStorage() {
        CertificateFileStorageService service = service("certificates");
        MockMultipartFile file = file("certificate.pdf", "application/pdf", new byte[10 * 1024 * 1024 + 1]);

        assertThatThrownBy(() -> service.upload(file))
                .isInstanceOf(ApiException.class)
                .hasMessage("Certificate files must be 10 MB or smaller");
    }

    private CertificateFileStorageService service(String bucket) {
        return new CertificateFileStorageService(
                bucket, "us-east-1", "", "", "", "", Duration.ofMinutes(15));
    }

    private MockMultipartFile file(String name, String contentType, byte[] content) {
        return new MockMultipartFile("file", name, contentType, content);
    }
}

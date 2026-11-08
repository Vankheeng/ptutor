package com.ptutor.backend.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import com.ptutor.backend.exception.ApiException;

class ComplaintFileStorageServiceTest {

    @Test
    void uploadExplainsWhenBucketIsNotConfigured() {
        ComplaintFileStorageService service = service("");
        MockMultipartFile file = file("evidence.png", "image/png", new byte[] {1, 2, 3});

        assertThatThrownBy(() -> service.upload(file))
                .isInstanceOf(ApiException.class)
                .hasMessage("Complaint file storage is not configured")
                .extracting("code")
                .isEqualTo("COMPLAINT_STORAGE_UNAVAILABLE");
    }

    @Test
    void uploadRejectsUnsupportedFileTypeBeforeCallingStorage() {
        ComplaintFileStorageService service = service("complaints");
        MockMultipartFile file = file("evidence.zip", "application/zip", new byte[] {1, 2, 3});

        assertThatThrownBy(() -> service.upload(file))
                .isInstanceOf(ApiException.class)
                .hasMessage("Complaint evidence files must be PDF, image, video, TXT or Word files");
    }

    @Test
    void uploadRejectsFilesLargerThanTenMegabytesBeforeCallingStorage() {
        ComplaintFileStorageService service = service("complaints");
        MockMultipartFile file = file("evidence.png", "image/png", new byte[10 * 1024 * 1024 + 1]);

        assertThatThrownBy(() -> service.upload(file))
                .isInstanceOf(ApiException.class)
                .hasMessage("Complaint evidence files must be 10 MB or smaller");
    }

    private ComplaintFileStorageService service(String bucket) {
        return new ComplaintFileStorageService(
                bucket, "us-east-1", "", "", "", "", Duration.ofMinutes(15));
    }

    private MockMultipartFile file(String name, String contentType, byte[] content) {
        return new MockMultipartFile("file", name, contentType, content);
    }
}

package com.ptutor.backend.service;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.ptutor.backend.exception.ApiException;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

@Service
public class CertificateFileStorageService {

    private static final Logger log = LoggerFactory.getLogger(CertificateFileStorageService.class);
    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private final String bucket;
    private final String region;
    private final String endpoint;
    private final String publicEndpoint;
    private final String accessKey;
    private final String secretKey;
    private final Duration presignDuration;

    public CertificateFileStorageService(
            @Value("${app.storage.s3.bucket:}") String bucket,
            @Value("${app.storage.s3.region:us-east-1}") String region,
            @Value("${app.storage.s3.endpoint:}") String endpoint,
            @Value("${app.storage.s3.public-endpoint:}") String publicEndpoint,
            @Value("${app.storage.s3.access-key:}") String accessKey,
            @Value("${app.storage.s3.secret-key:}") String secretKey,
            @Value("${app.storage.s3.presign-duration:PT15M}") Duration presignDuration) {
        this.bucket = bucket;
        this.region = region;
        this.endpoint = endpoint;
        this.publicEndpoint = publicEndpoint;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.presignDuration = presignDuration;
    }

    public String upload(MultipartFile file) {
        validate(file);
        ensureConfigured();
        String contentType = file.getContentType().toLowerCase(Locale.ROOT);
        String extension = contentType.equals("application/pdf") ? ".pdf"
                : contentType.equals("image/png") ? ".png" : ".jpg";
        String key = "certificates/" + UUID.randomUUID() + extension;
        try (S3Client client = createClient()) {
            client.putObject(PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(contentType)
                    .contentLength(file.getSize())
                    .build(), software.amazon.awssdk.core.sync.RequestBody.fromBytes(file.getBytes()));
            return "s3://" + bucket + "/" + key;
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "CERTIFICATE_UPLOAD_FAILED",
                    "The certificate file could not be read");
        } catch (S3Exception exception) {
            log.error("Object storage rejected certificate upload (status {}, code {})",
                    exception.statusCode(), exception.awsErrorDetails() == null
                            ? "unknown" : exception.awsErrorDetails().errorCode(), exception);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "CERTIFICATE_STORAGE_REJECTED",
                    "Object storage rejected the upload. Check the bucket, region, and write permissions.");
        } catch (SdkClientException | IllegalArgumentException exception) {
            log.error("Could not connect to object storage for certificate upload", exception);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "CERTIFICATE_STORAGE_UNREACHABLE",
                    "Certificate storage could not be reached. Check the storage endpoint and credentials.");
        }
    }

    public String accessUrl(String reference) {
        if (reference == null || !reference.startsWith("s3://")) return reference;
        ensureConfigured();
        String prefix = "s3://" + bucket + "/";
        if (!reference.startsWith(prefix)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "CERTIFICATE_FILE_NOT_FOUND",
                    "The certificate file is unavailable");
        }
        String key = reference.substring(prefix.length());
        try (S3Presigner presigner = createPresigner()) {
            return presigner.presignGetObject(GetObjectPresignRequest.builder()
                    .signatureDuration(presignDuration)
                    .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(key).build())
                    .build()).url().toString();
        }
    }

    private void validate(MultipartFile file) {
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
    }

    private void ensureConfigured() {
        if (bucket == null || bucket.isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "CERTIFICATE_STORAGE_UNAVAILABLE",
                    "Certificate file storage is not configured");
        }
    }

    private S3Client createClient() {
        var builder = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(credentialsProvider())
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
        if (endpoint != null && !endpoint.isBlank()) builder.endpointOverride(URI.create(endpoint));
        return builder.build();
    }

    private S3Presigner createPresigner() {
        var builder = S3Presigner.builder()
                .region(Region.of(region))
                .credentialsProvider(credentialsProvider())
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
        String signingEndpoint = publicEndpoint == null || publicEndpoint.isBlank() ? endpoint : publicEndpoint;
        if (signingEndpoint != null && !signingEndpoint.isBlank()) builder.endpointOverride(URI.create(signingEndpoint));
        return builder.build();
    }

    private AwsCredentialsProvider credentialsProvider() {
        if (accessKey != null && !accessKey.isBlank() && secretKey != null && !secretKey.isBlank()) {
            return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
        }
        return DefaultCredentialsProvider.create();
    }
}

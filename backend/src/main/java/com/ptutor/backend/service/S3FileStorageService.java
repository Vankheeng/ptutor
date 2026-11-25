package com.ptutor.backend.service;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.ptutor.backend.exception.ApiException;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

@Service
public class S3FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(S3FileStorageService.class);

    private final String bucket;
    private final String region;
    private final String endpoint;
    private final String publicEndpoint;
    private final String accessKey;
    private final String secretKey;
    private final Duration presignDuration;

    public S3FileStorageService(
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

    public String upload(
            MultipartFile file,
            String prefix,
            String extension,
            UploadErrors errors) {
        ensureConfigured(errors.unavailableCode(), errors.unavailableMessage());
        String key = prefix + UUID.randomUUID() + extension;
        try (S3Client client = createClient()) {
            client.putObject(PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(file.getContentType())
                    .contentLength(file.getSize())
                    .build(), RequestBody.fromBytes(file.getBytes()));
            return "s3://" + bucket + "/" + key;
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    errors.readErrorCode(), errors.readErrorMessage());
        } catch (S3Exception exception) {
            log.error("Object storage rejected file upload (status {}, code {}, requestId {}, message {})",
                    exception.statusCode(),
                    exception.awsErrorDetails() == null ? "unknown" : exception.awsErrorDetails().errorCode(),
                    exception.requestId(),
                    exception.awsErrorDetails() == null ? "unknown" : exception.awsErrorDetails().errorMessage(),
                    exception);
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    errors.rejectedErrorCode(), errors.rejectedErrorMessage());
        } catch (SdkClientException | IllegalArgumentException exception) {
            log.error("Could not connect to object storage for file upload", exception);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    errors.unreachableErrorCode(), errors.unreachableErrorMessage());
        }
    }

    public String accessUrl(
            String reference,
            String unavailableErrorCode,
            String unavailableErrorMessage,
            String notFoundErrorCode,
            String notFoundErrorMessage) {
        if (reference == null || !reference.startsWith("s3://")) return reference;
        ensureConfigured(unavailableErrorCode, unavailableErrorMessage);
        String prefix = "s3://" + bucket + "/";
        if (!reference.startsWith(prefix)) {
            throw new ApiException(HttpStatus.NOT_FOUND, notFoundErrorCode, notFoundErrorMessage);
        }
        String key = reference.substring(prefix.length());
        try (S3Presigner presigner = createPresigner()) {
            return presigner.presignGetObject(GetObjectPresignRequest.builder()
                    .signatureDuration(presignDuration)
                    .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(key).build())
                    .build()).url().toString();
        }
    }

    private void ensureConfigured(String errorCode, String errorMessage) {
        if (bucket == null || bucket.isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, errorCode, errorMessage);
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

    public record UploadErrors(
            String unavailableCode,
            String unavailableMessage,
            String readErrorCode,
            String readErrorMessage,
            String rejectedErrorCode,
            String rejectedErrorMessage,
            String unreachableErrorCode,
            String unreachableErrorMessage) { }
}

/*
 * Copyright Red Hat, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Red Hat trademarks are not licensed under GPLv3. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package com.redhat.swatch.utilization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyExistsException;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

@Testcontainers
class S3UploaderTest {

  private static final String MINIO_ACCESS_KEY = "minioadmin";
  private static final String MINIO_SECRET_KEY = "minioadmin";
  private static final String TEST_BUCKET = "test-heap-dumps";

  @Container
  private static final GenericContainer<?> minioContainer =
      new GenericContainer<>(DockerImageName.parse("minio/minio:RELEASE.2024-10-02T17-50-41Z"))
          .withExposedPorts(9000)
          .withEnv("MINIO_ROOT_USER", MINIO_ACCESS_KEY)
          .withEnv("MINIO_ROOT_PASSWORD", MINIO_SECRET_KEY)
          .withCommand("server", "/data");

  private static S3Client s3Client;
  private static String minioEndpoint;

  @BeforeAll
  static void setUpAll() {
    String minioHost = minioContainer.getHost();
    Integer minioPort = minioContainer.getMappedPort(9000);
    minioEndpoint = String.format("http://%s:%d", minioHost, minioPort);

    // Create S3 client for verification
    AwsBasicCredentials credentials =
        AwsBasicCredentials.create(MINIO_ACCESS_KEY, MINIO_SECRET_KEY);
    s3Client =
        S3Client.builder()
            .region(Region.US_EAST_1)
            .credentialsProvider(StaticCredentialsProvider.create(credentials))
            .endpointOverride(URI.create(minioEndpoint))
            .forcePathStyle(true)
            .build();

    // Create test S3 bucket
    try {
      s3Client.createBucket(CreateBucketRequest.builder().bucket(TEST_BUCKET).build());
    } catch (BucketAlreadyExistsException | BucketAlreadyOwnedByYouException e) {
      // Bucket already exists from a previous run
    }
  }

  @AfterAll
  static void tearDown() {
    if (s3Client != null) {
      s3Client.close();
    }
  }

  @Test
  void testUploadFileWithEndpoint() throws Exception {
    // Create temp file
    String testContent = "Test heap dump content\nLine 2\nLine 3";
    Path tempFile = Files.createTempFile("test-upload", ".txt");

    try {
      Files.writeString(tempFile, testContent);

      String s3Key = "test/upload-with-endpoint.txt";
      S3Uploader.uploadFileWithCredentials(
          tempFile.toString(),
          TEST_BUCKET,
          s3Key,
          minioEndpoint,
          MINIO_ACCESS_KEY,
          MINIO_SECRET_KEY,
          "us-east-1");

      // Verify file was uploaded by reading it back
      try (ResponseInputStream<GetObjectResponse> response =
          s3Client.getObject(GetObjectRequest.builder().bucket(TEST_BUCKET).key(s3Key).build())) {
        String downloadedContent = new String(response.readAllBytes());
        assertEquals(testContent, downloadedContent, "Uploaded content should match original");
      }

    } finally {
      Files.deleteIfExists(tempFile);
    }
  }

  @Test
  void testUploadNonExistentFile() {
    // Test error handling for non-existent file
    String nonExistentFile = "/tmp/does-not-exist-" + System.currentTimeMillis() + ".txt";
    String s3Key = "test/missing.txt";

    IOException exception =
        assertThrows(
            IOException.class,
            () ->
                S3Uploader.uploadFileWithCredentials(
                    nonExistentFile,
                    TEST_BUCKET,
                    s3Key,
                    minioEndpoint,
                    MINIO_ACCESS_KEY,
                    MINIO_SECRET_KEY,
                    "us-east-1"));

    assertTrue(
        exception.getMessage().contains("File not found"), "Should report file not found error");
  }

  @Test
  void testUploadWithMissingCredentials() throws Exception {
    Path tempFile = Files.createTempFile("test-no-creds", ".txt");
    try {
      Files.writeString(tempFile, "test");

      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () ->
                  S3Uploader.uploadFileWithCredentials(
                      tempFile.toString(),
                      TEST_BUCKET,
                      "test/key.txt",
                      minioEndpoint,
                      null,
                      null,
                      null));

      assertTrue(
          exception.getMessage().contains("AWS credentials not found"),
          "Should report missing credentials");
    } finally {
      Files.deleteIfExists(tempFile);
    }
  }
}

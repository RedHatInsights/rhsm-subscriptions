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

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Standalone S3 uploader for heap dumps. Called from heap-monitor.sh to upload files without
 * requiring AWS CLI.
 *
 * <p>Usage: java -cp /deployments/quarkus-run.jar com.redhat.swatch.utilization.S3Uploader \
 * /path/to/file.gz \ bucket-name \ s3/key/path/file.gz
 *
 * <p>Credentials read from environment variables: AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY
 * AWS_DEFAULT_REGION
 */
public class S3Uploader {
  public static void main(String[] args) {
    // Disable JBoss LogManager requirement when running standalone
    System.setProperty("java.util.logging.manager", "java.util.logging.LogManager");

    if (args.length < 3) {
      System.err.println("Usage: S3Uploader <file-path> <bucket> <s3-key> [endpoint-url]");
      System.err.println(
          "Environment variables required: AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY");
      System.exit(1);
    }

    String filePath = args[0];
    String bucket = args[1];
    String s3Key = args[2];
    String endpointUrl = args.length > 3 ? args[3] : null;

    try {
      uploadFile(filePath, bucket, s3Key, endpointUrl);
      System.out.println("Successfully uploaded " + filePath + " to s3://" + bucket + "/" + s3Key);
    } catch (Exception e) {
      System.err.println("Failed to upload " + filePath + ": " + e.getMessage());
      e.printStackTrace();
      System.exit(1);
    }
  }

  static void uploadFile(String filePath, String bucket, String s3Key, String endpointUrl)
      throws IOException {
    // Read credentials from environment (set by heap-monitor.sh from /aws/* files)
    String accessKeyId = System.getenv("AWS_ACCESS_KEY_ID");
    String secretAccessKey = System.getenv("AWS_SECRET_ACCESS_KEY");
    String region = System.getenv("AWS_DEFAULT_REGION");

    uploadFileWithCredentials(
        filePath, bucket, s3Key, endpointUrl, accessKeyId, secretAccessKey, region);
  }

  static void uploadFileWithCredentials(
      String filePath,
      String bucket,
      String s3Key,
      String endpointUrl,
      String accessKeyId,
      String secretAccessKey,
      String region)
      throws IOException {
    if (accessKeyId == null || secretAccessKey == null) {
      throw new IllegalStateException(
          "AWS credentials not found in environment. Required: AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY");
    }

    if (region == null) {
      region = "us-east-1";
    }

    Path path = Paths.get(filePath);
    if (!Files.exists(path)) {
      throw new IOException("File not found: " + filePath);
    }

    // Create S3 client with credentials
    AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKeyId, secretAccessKey);
    S3ClientBuilder builder =
        S3Client.builder()
            .region(Region.of(region))
            .credentialsProvider(StaticCredentialsProvider.create(credentials));

    // Add endpoint override for S3-compatible storage (MinIO, etc.)
    if (endpointUrl != null && !endpointUrl.isEmpty()) {
      builder.endpointOverride(URI.create(endpointUrl)).forcePathStyle(true);
    }

    try (S3Client s3 = builder.build()) {
      // Upload file
      PutObjectRequest putRequest = PutObjectRequest.builder().bucket(bucket).key(s3Key).build();
      s3.putObject(putRequest, RequestBody.fromFile(path));
    }
  }
}

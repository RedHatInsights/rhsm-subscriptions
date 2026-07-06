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
package com.redhat.swatch.aws.configuration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.DeploymentException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.profiles.ProfileFile;

@Dependent
@Slf4j
public class AwsProfileFileConfiguration {

  private static final Set<String> TEST_CREDENTIAL_PROFILES = Set.of("test", "dev", "ephemeral");

  @ApplicationScoped
  @Produces
  public ProfileFile defaultProfileFile(
      @ConfigProperty(name = "quarkus.profile", defaultValue = "prod") String profile,
      @ConfigProperty(name = "aws.marketplace.test-credentials.seller-profile")
          String sellerProfile,
      @ConfigProperty(name = "aws.marketplace.test-credentials.access-key-id") String accessKeyId,
      @ConfigProperty(name = "aws.marketplace.test-credentials.secret-access-key")
          String secretAccessKey) {

    if (TEST_CREDENTIAL_PROFILES.contains(profile)) {
      log.debug(
          "Using test AWS marketplace credentials profile '{}' for quarkus profile '{}'",
          sellerProfile,
          profile);
      return syntheticProfileFile(sellerProfile, accessKeyId, secretAccessKey);
    }

    String awsConfigFile = System.getenv("AWS_CONFIG_FILE");
    if (awsConfigFile == null || awsConfigFile.isBlank()) {
      throw new DeploymentException(
          "AWS_CONFIG_FILE is required for quarkus profile '%s'".formatted(profile));
    } else if (!Files.isRegularFile(Path.of(awsConfigFile))) {
      throw new DeploymentException(
          "AWS marketplace credentials file not found: %s".formatted(awsConfigFile));
    }

    return ProfileFile.defaultProfileFile();
  }

  private static ProfileFile syntheticProfileFile(
      String sellerProfile, String accessKeyId, String secretAccessKey) {
    String configContent =
        """
        [profile %s]
        aws_access_key_id = %s
        aws_secret_access_key = %s
        """
            .formatted(sellerProfile, accessKeyId, secretAccessKey);
    return ProfileFile.builder()
        .content(configContent)
        .type(ProfileFile.Type.CONFIGURATION)
        .build();
  }
}

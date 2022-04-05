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
package com.redhat.swatch.files;

import com.redhat.swatch.exception.AwsMissingCredentialsException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;
import javax.enterprise.context.ApplicationScoped;
import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;

/**
 * Loads AWS credentials used for multiple accounts from JSON.
 *
 * <p>Intended to support multiple seller accounts needing separate credentials
 *
 * <p>Example JSON: [ { "accessKeyId": "placeholder", "secretAccessKey": "placeholder",
 * "sellerAccount": "account123" } ]
 */
@ApplicationScoped
@Slf4j
public class AwsCredentialsLookup {

  private Map<String, StaticCredentialsProvider> awsCredentialMap = Collections.emptyMap();

  public AwsCredentialsLookup(
      @ConfigProperty(name = "AWS_CREDENTIALS_JSON") String credentialsJson) {
    try (Jsonb jsonb = JsonbBuilder.create()) {
      AwsSellerAccountCredentials[] awsSellerAccountCredentials =
          jsonb.fromJson(credentialsJson, AwsSellerAccountCredentials[].class);
      awsCredentialMap =
          Arrays.stream(awsSellerAccountCredentials)
              .collect(
                  Collectors.toMap(
                      AwsSellerAccountCredentials::getSellerAccount,
                      StaticCredentialsProvider::create));
    } catch (Exception e) {
      log.warn("Unable to read AWS credentials from JSON.", e);
    }
  }

  public AwsCredentialsProvider getCredentialsProvider(String awsSellerAccount) {
    StaticCredentialsProvider credentialsProvider = awsCredentialMap.get(awsSellerAccount);
    if (credentialsProvider == null) {
      throw new AwsMissingCredentialsException(awsSellerAccount);
    }
    return credentialsProvider;
  }
}

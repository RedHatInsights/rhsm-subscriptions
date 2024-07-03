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
package com.redhat.swatch.aws.file;

import com.redhat.swatch.aws.exception.AwsMissingCredentialsException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.profiles.ProfileFile;

/**
 * Loads AWS credentials by looking them up in AWS config by profile name.
 *
 * <p>Assumes the profile name will be the seller account. Creates and caches a single
 * ProfileCredentialsProvider per seller account, in order to avoid repeated role assumption API
 * operations.
 */
@ApplicationScoped
@Slf4j
public class AwsCredentialsLookup {

  private final Map<String, ProfileCredentialsProvider> awsCredentialMap = new HashMap<>();

  @Inject ProfileFile profileFile;

  public synchronized AwsCredentialsProvider getCredentialsProvider(String awsSellerAccount) {
    return awsCredentialMap.computeIfAbsent(awsSellerAccount, this::createCredentialsFromProfile);
  }

  private ProfileCredentialsProvider createCredentialsFromProfile(String awsSellerAccount) {
    // NOTE: it is useful to do this check here, because it allows us to catch missing credentials
    // in a predictable way.
    if (profileFile.profile(awsSellerAccount).isEmpty()) {
      throw new AwsMissingCredentialsException(awsSellerAccount);
    }
    return ProfileCredentialsProvider.create(awsSellerAccount);
  }
}

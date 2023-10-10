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
package com.redhat.swatch.aws.files;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.redhat.swatch.aws.exception.AwsMissingCredentialsException;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.profiles.Profile;
import software.amazon.awssdk.profiles.ProfileFile;

@QuarkusTest
class AwsCredentialsLookupTest {
  private static final String AWS_SELLER_ACCOUNT = "123";

  @InjectMock ProfileFile mockProfileFile;
  @Inject AwsCredentialsLookup lookup;

  @Test
  void testGetCredentialsProviderShouldThrowAwsMissingCredentialsException() {
    givenUnknownAwsSellerAccount();
    assertThrows(AwsMissingCredentialsException.class, this::whenGetCredentialsProvider);
  }

  @Test
  void testGetCredentialsProviderShouldReturnProvider() {
    givenKnownAwsSellerAccount();
    AwsCredentialsProvider provider = whenGetCredentialsProvider();
    assertTrue(provider instanceof ProfileCredentialsProvider);
  }

  private void givenUnknownAwsSellerAccount() {
    when(mockProfileFile.profile(AWS_SELLER_ACCOUNT)).thenReturn(Optional.empty());
  }

  private void givenKnownAwsSellerAccount() {
    when(mockProfileFile.profile(AWS_SELLER_ACCOUNT))
        .thenReturn(Optional.of(Profile.builder().name("any").properties(Map.of()).build()));
  }

  private AwsCredentialsProvider whenGetCredentialsProvider() {
    return lookup.getCredentialsProvider(AWS_SELLER_ACCOUNT);
  }
}

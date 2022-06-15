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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.redhat.swatch.exception.AwsMissingCredentialsException;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsCredentials;

class AwsCredentialsLookupTest {
  @Test
  void testCanLookupWithSingleAccount() {
    AwsCredentials credentials =
        new AwsCredentialsLookup(
                "[{"
                    + "\"accessKeyId\":\"placeholder\","
                    + "\"secretAccessKey\":\"placeholder2\","
                    + "\"sellerAccount\":\"account123\""
                    + "}]")
            .getCredentialsProvider("account123")
            .resolveCredentials();
    assertEquals("placeholder", credentials.accessKeyId());
    assertEquals("placeholder2", credentials.secretAccessKey());
  }

  @Test
  void testCanLookupWithTwoAccounts() {
    AwsCredentials credentials =
        new AwsCredentialsLookup(
                "[{"
                    + "\"accessKeyId\":\"placeholder\","
                    + "\"secretAccessKey\":\"placeholder2\","
                    + "\"sellerAccount\":\"account123\""
                    + "},{"
                    + "\"accessKeyId\":\"placeholder3\","
                    + "\"secretAccessKey\":\"placeholder4\","
                    + "\"sellerAccount\":\"account456\""
                    + "}]")
            .getCredentialsProvider("account456")
            .resolveCredentials();
    assertEquals("placeholder3", credentials.accessKeyId());
    assertEquals("placeholder4", credentials.secretAccessKey());
  }

  @Test
  void testThrowsExceptionIfCredentialMissing() {
    AwsCredentialsLookup awsCredentialsLookup =
        new AwsCredentialsLookup(
            "[{"
                + "\"accessKeyId\":\"placeholder\","
                + "\"secretAccessKey\":\"placeholder2\","
                + "\"sellerAccount\":\"account123\""
                + "}]");
    assertThrows(
        AwsMissingCredentialsException.class,
        () -> {
          awsCredentialsLookup.getCredentialsProvider("missing-account");
        });
  }

  @Test
  void testNoExceptionIfMisconfigured() {
    AwsCredentialsLookup awsCredentialsLookup = new AwsCredentialsLookup("[");
    assertThrows(
        AwsMissingCredentialsException.class,
        () -> awsCredentialsLookup.getCredentialsProvider("foobar"));
  }
}

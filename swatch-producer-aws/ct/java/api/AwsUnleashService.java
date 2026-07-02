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
package api;

import com.redhat.swatch.component.tests.api.UnleashService;
import com.redhat.swatch.component.tests.utils.AwaitilitySettings;
import com.redhat.swatch.component.tests.utils.AwaitilityUtils;

public class AwsUnleashService extends UnleashService {

  public static final String USE_CUSTOMER_AWS_ACCOUNT_ID =
      "swatch.swatch-producer-aws.use-customer-aws-account-id";

  public void enableUseCustomerAwsAccountId() {
    enableFlag(USE_CUSTOMER_AWS_ACCOUNT_ID);
    AwaitilityUtils.until(
        () -> isFlagEnabled(USE_CUSTOMER_AWS_ACCOUNT_ID),
        enabled -> enabled,
        AwaitilitySettings.defaults()
            .timeoutMessage(
                "Unleash toggle '%s' should be enabled".formatted(USE_CUSTOMER_AWS_ACCOUNT_ID)));
    waitForUnleashPropagation();
  }

  public void disableUseCustomerAwsAccountId() {
    disableFlag(USE_CUSTOMER_AWS_ACCOUNT_ID);
  }

  /**
   * Wait for swatch-producer-aws to pick up the toggle. The harness only checks Unleash admin; the
   * service polls on {@code quarkus.unleash.fetch-toggles-interval} (1s in dev/ephemeral).
   */
  private void waitForUnleashPropagation() {
    try {
      Thread.sleep(2000);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while waiting for Unleash propagation", e);
    }
  }
}

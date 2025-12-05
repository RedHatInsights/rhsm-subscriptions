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
package tests;

import api.MessageValidators;
import org.junit.jupiter.api.Test;
import utils.ConduitTestHelpers;

import static com.redhat.swatch.component.tests.utils.Topics.INVENTORY_HOST_INGRESS;

/**
 * Component test that validates the flow of candlepin records through system-conduit:
 *
 * <p>1. Creates a task message on platform.rhsm-conduit.tasks topic 2. System-conduit processes the
 * task and syncs from RHSM API (stub) 3. Validates the output on platform.inventory.host-ingress
 * topic
 *
 * <p>This test is based on test_validate_tally_on_public_cloud_with_usage from the IQE test suite.
 */
public class PublicCloudConduitComponentTest extends BaseConduitComponentTest {

  ConduitTestHelpers helpers = new ConduitTestHelpers();

  @Test
  public void testPublicCloudHostSync() {
    // Step 1: Sync conduit for the test orgId
    // This will queue a task message to Kafka which will be consumed by system-conduit
    helpers.syncConduitByOrgId(service, orgId);

    // Step 2: Wait for the conduit to process the task and send host data to inventory
    // The stub RHSM API will return canned consumer data
    // The message validator ensures the message matches the expected orgId
    kafkaBridge.waitForKafkaMessage(
        INVENTORY_HOST_INGRESS, MessageValidators.hostMessageMatchesOrgId(orgId));
  }
}

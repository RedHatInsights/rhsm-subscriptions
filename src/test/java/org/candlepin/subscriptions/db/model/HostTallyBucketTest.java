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
package org.candlepin.subscriptions.db.model;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class HostTallyBucketTest {

  // This is necessary to avoid duplicate keys with null hostId.
  @Test
  void testBucketSetHostAlsoSetsBucketKeyHostId() {
    Host host = Mockito.mock(Host.class);
    HostTallyBucket hostTallyBucket =
        new HostTallyBucket(
            host,
            "product123",
            ServiceLevel.PREMIUM,
            Usage.PRODUCTION,
            BillingProvider.RED_HAT,
            null,
            false,
            4,
            4,
            HardwareMeasurementType.PHYSICAL);

    when(host.getId()).thenReturn(UUID.randomUUID());

    hostTallyBucket.setHost(host);

    assertEquals(host.getId(), hostTallyBucket.getKey().getHostId());
  }

  // Set bucket key host ID to null when the host is null.
  @Test
  void testBucketSetNullHostAlsoSetsBucketKeyHostId() {
    HostTallyBucket hostTallyBucket =
        new HostTallyBucket(
            null,
            "product123",
            ServiceLevel.PREMIUM,
            Usage.PRODUCTION,
            BillingProvider.RED_HAT,
            null,
            false,
            4,
            4,
            HardwareMeasurementType.PHYSICAL);

    hostTallyBucket.setHost(null);

    assertNull(hostTallyBucket.getKey().getHostId());
  }
}

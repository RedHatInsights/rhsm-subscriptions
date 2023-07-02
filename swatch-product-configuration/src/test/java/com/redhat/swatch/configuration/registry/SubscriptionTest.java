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
package com.redhat.swatch.configuration.registry;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

class SubscriptionTest {

  @SneakyThrows
  @Test
  void testFindServiceTypeMatch() {

    var rosaSubscription = Subscription.findByServiceType("rosa Instance");

    var expected = "rosa";
    var actual = rosaSubscription.get().getId();

    assertEquals(expected, actual);
  }

  @SneakyThrows
  @Test
  void testFindServiceTypeNoMatch() {

    var expected = Optional.empty();
    var actual = Subscription.findByServiceType("bananas");

    assertEquals(expected, actual);
  }

  @SneakyThrows
  @Test
  void testFindByArchNoMatch() {

    var expected = Optional.empty();
    var actual = Subscription.findByArch("bananas");

    assertEquals(expected, actual);
  }

  @SneakyThrows
  @Test
  void testGetAllServiceTypes() {

    var expected =
        List.of(
            "OpenShift Cluster",
            "OpenShift Cluster",
            "BASILISK Instance",
            "Rhacs Cluster",
            "Rhods Cluster",
            "Kafka Cluster",
            "rosa Instance");
    var actual = Subscription.getAllServiceTypes();

    assertEquals(expected, actual);
  }

  @SneakyThrows
  @Test
  void testFindByArchMatch() {

    var rhelForIbmZ = Subscription.findByArch("s390x");

    var expected = "RHEL for IBM z";
    var actual = rhelForIbmZ.get().getId();

    assertEquals(expected, actual);
  }

  @Test
  void testFindById() throws IOException {

    var expected = "basilisk";
    var actual = Subscription.findById("basilisk").get().getId();

    assertEquals(expected, actual);
  }
}

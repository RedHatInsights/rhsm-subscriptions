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
package org.candlepin.subscriptions.capacity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CapacityProductExtractorTest {

  public static final String RHEL_LINUX_ID = "69";
  public static final String SATELLITE_SERVER_ID = "250";
  public static final String RHEL_x86_ID = "479";
  public static final String RHEL_WORKSTATION_ID = "71";
  public static final String OPENSHIFT_CONTAINER_PLATFORM_ID = "290";
  public static final String RHEL_SERVER = "RHEL Server";
  public static final String SATELLITE_SERVER = "Satellite Server";
  public static final String OPEN_SHIFT_CONTAINER_PLATFORM = "OpenShift Container Platform";
  private CapacityProductExtractor extractor;

  @BeforeAll
  void setup() {
    extractor = new CapacityProductExtractor();
  }

  @ParameterizedTest
  @MethodSource("generateProductIdMappings")
  void testSwatchSubscriptionProductIdsMappings(List<String> engProductId, Set<String> tagNames) {

    var actual = extractor.getProducts(engProductId);

    assertEquals(tagNames, actual);
  }

  static Stream<Arguments> generateProductIdMappings() {
    return Stream.of(
        Arguments.of(List.of(), Set.of()),
        Arguments.of(List.of("123"), Set.of()),
        Arguments.of(List.of("123", RHEL_LINUX_ID), Set.of(RHEL_SERVER)),
        Arguments.of(List.of(RHEL_LINUX_ID), Set.of(RHEL_SERVER)),
        Arguments.of(List.of(RHEL_LINUX_ID, SATELLITE_SERVER_ID), Set.of(SATELLITE_SERVER)),
        Arguments.of(List.of(RHEL_x86_ID, SATELLITE_SERVER_ID), Set.of(SATELLITE_SERVER)),
        Arguments.of(List.of(RHEL_LINUX_ID, SATELLITE_SERVER_ID), Set.of(SATELLITE_SERVER)),
        Arguments.of(List.of(RHEL_x86_ID), Set.of(RHEL_SERVER)),
        Arguments.of(List.of(RHEL_WORKSTATION_ID, SATELLITE_SERVER_ID), Set.of(SATELLITE_SERVER)),
        Arguments.of(
            List.of(OPENSHIFT_CONTAINER_PLATFORM_ID, RHEL_x86_ID),
            Set.of(OPEN_SHIFT_CONTAINER_PLATFORM)),
        Arguments.of(
            List.of(RHEL_LINUX_ID, OPENSHIFT_CONTAINER_PLATFORM_ID),
            Set.of(OPEN_SHIFT_CONTAINER_PLATFORM)),
        Arguments.of(
            List.of(OPENSHIFT_CONTAINER_PLATFORM_ID, SATELLITE_SERVER_ID),
            Set.of(OPEN_SHIFT_CONTAINER_PLATFORM, SATELLITE_SERVER)));
  }
}

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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;
import org.candlepin.subscriptions.registry.TagProfile;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CapacityProductExtractorTest {

  public static final int RHEL_LINUX_ID = 69;
  public static final int SATELLITE_SERVER_ID = 250;
  public static final int RHEL_x86_ID = 479;
  public static final int RHEL_WORKSTATION_ID = 71;
  public static final int OPENSHIFT_CONTAINER_PLATFORM_ID = 290;
  public static final String RHEL_SERVER = "RHEL Server";
  public static final String SATELLITE_SERVER = "Satellite Server";
  public static final String OPEN_SHIFT_CONTAINER_PLATFORM = "OpenShift Container Platform";
  private CapacityProductExtractor extractor;
  TagProfile tagProfile;

  @BeforeAll
  void setup() throws IOException {
    ResourceLoader resourceLoader = new DefaultResourceLoader();
    Yaml parser = new Yaml(new Constructor(TagProfile.class));
    tagProfile =
        parser.load(resourceLoader.getResource("classpath:test_tag_profile.yaml").getInputStream());
    extractor = new CapacityProductExtractor(tagProfile);
  }

  @Test
  void productExtractorReturnsExpectedProducts() {
    Set<String> products = extractor.getProducts(Arrays.asList("6", "9", "10"));
    assertThat(
        products, Matchers.containsInAnyOrder("RHEL", "NOT RHEL", "RHEL Workstation", RHEL_SERVER));
  }

  @Test
  void productExtractorReturnsNoProductsIfNoProductIdsMatch() {
    Set<String> products = extractor.getProducts(Collections.singletonList("42"));
    assertThat(products, Matchers.empty());
  }

  @Test
  void productSetIsInvalid() {

    // TODO these are not productNames from tagprofile...they look like roles

    Set<String> products = new HashSet<>();
    products.add("RHEL Workstation");
    products.add("Satellite");
    assertThat(extractor.setIsInvalid(products), Matchers.is(true));
  }

  @Test
  void productSetIsInvalidWithOpenShift() {
    Set<String> products = new HashSet<>();
    products.add("RHEL Workstation");
    products.add(OPEN_SHIFT_CONTAINER_PLATFORM);
    assertThat(extractor.setIsInvalid(products), Matchers.is(true));
  }

  @Test
  void productSetIsValid() {
    Set<String> products = new HashSet<>();
    products.add("RHEL Workstation");
    products.add("RHEL");
    products.add("RHEL for x86");
    assertThat(extractor.setIsInvalid(products), Matchers.is(false));
  }

  @Test
  void productExtractorReturnsExpectedProductsWhenSatellitePresent() {
    Set<String> products = extractor.getProducts(Arrays.asList("1", "269"));

    System.out.println(products);

    assertThat(products, Matchers.containsInAnyOrder("Satellite", "Satellite Capsule"));
  }

  @Test
  void productExtractorReturnsExpectedProductsWhenOpenShiftPresent() {
    Set<String> products = extractor.getProducts(Arrays.asList("1", "13"));
    assertThat(products, Matchers.containsInAnyOrder(OPEN_SHIFT_CONTAINER_PLATFORM));
  }

  @ParameterizedTest
  @MethodSource("generateProductIdMappings")
  void testSwatchSubscriptionProductIdsMappings(Set<Integer> engProductId, Set<String> tagNames) {

    var actual = extractor.getProductTagsFrom(engProductId.stream());

    assertEquals(tagNames, actual);
  }

  static Stream<Arguments> generateProductIdMappings() {
    return Stream.of(
        Arguments.of(Set.of(), Set.of()),
        Arguments.of(Set.of(123), Set.of()),
        Arguments.of(Set.of(123, RHEL_LINUX_ID), Set.of(RHEL_SERVER)),
        Arguments.of(Set.of(RHEL_LINUX_ID), Set.of(RHEL_SERVER)),
        Arguments.of(Set.of(RHEL_LINUX_ID, SATELLITE_SERVER_ID), Set.of(SATELLITE_SERVER)),
        Arguments.of(Set.of(RHEL_x86_ID, SATELLITE_SERVER_ID), Set.of(SATELLITE_SERVER)),
        Arguments.of(Set.of(RHEL_LINUX_ID, SATELLITE_SERVER_ID), Set.of(SATELLITE_SERVER)),
        Arguments.of(Set.of(RHEL_x86_ID), Set.of(RHEL_SERVER)),
        Arguments.of(Set.of(RHEL_WORKSTATION_ID, SATELLITE_SERVER_ID), Set.of(SATELLITE_SERVER)),
        Arguments.of(
            Set.of(OPENSHIFT_CONTAINER_PLATFORM_ID, RHEL_x86_ID),
            Set.of(OPEN_SHIFT_CONTAINER_PLATFORM)),
        Arguments.of(
            Set.of(RHEL_LINUX_ID, OPENSHIFT_CONTAINER_PLATFORM_ID),
            Set.of(OPEN_SHIFT_CONTAINER_PLATFORM)),
        Arguments.of(
            Set.of(OPENSHIFT_CONTAINER_PLATFORM_ID, SATELLITE_SERVER_ID),
            Set.of(OPEN_SHIFT_CONTAINER_PLATFORM, SATELLITE_SERVER)));
  }
}

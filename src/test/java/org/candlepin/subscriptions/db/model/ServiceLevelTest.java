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

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.common.collect.Sets;
import java.util.Set;
import java.util.stream.Collectors;
import org.candlepin.subscriptions.util.ApiModelMapperV1;
import org.candlepin.subscriptions.util.ApiModelMapperV1Impl;
import org.candlepin.subscriptions.util.ApiModelMapperV2;
import org.candlepin.subscriptions.util.ApiModelMapperV2Impl;
import org.candlepin.subscriptions.utilization.api.v2.model.ServiceLevelType;
import org.junit.jupiter.api.Test;

class ServiceLevelTest {

  public static final String PREMIUM_MIXED_CASE = "PreMIUm";
  public static final String BAD_VALUE = "platinum";

  @Test
  void testEachValueSurvivesStringConversion() {
    ServiceLevel.EnumConverter converter = new ServiceLevel.EnumConverter();
    for (ServiceLevel sla : ServiceLevel.values()) {
      assertEquals(converter.convertToDatabaseColumn(sla), sla.getValue());
      assertEquals(converter.convertToEntityAttribute(sla.getValue()), sla);
    }
  }

  @Test
  void testFromStringEmptyString() {
    assertEquals(ServiceLevel.EMPTY, ServiceLevel.fromString(""));
  }

  @Test
  void testFromStringInvalidValueDefaultUnspecified() {
    assertEquals(ServiceLevel.EMPTY, ServiceLevel.fromString(BAD_VALUE));
  }

  @Test
  void testFromStringNullDefaultUnspecified() {
    assertEquals(ServiceLevel.EMPTY, ServiceLevel.fromString(null));
  }

  @Test
  void testFromStringUpperCase() {
    assertEquals(ServiceLevel.PREMIUM, ServiceLevel.fromString(PREMIUM_MIXED_CASE.toUpperCase()));
  }

  @Test
  void testFromStringLowerCase() {

    assertEquals(ServiceLevel.PREMIUM, ServiceLevel.fromString(PREMIUM_MIXED_CASE.toLowerCase()));
  }

  @Test
  void testFromStringMixedCase() {
    assertEquals(ServiceLevel.PREMIUM, ServiceLevel.fromString(PREMIUM_MIXED_CASE));
  }

  @Test
  void testOpenApiEnumValuesMatchV2() {
    ApiModelMapperV2 mapper = new ApiModelMapperV2Impl();
    Set<ServiceLevelType> expected = Sets.newHashSet(ServiceLevelType.class.getEnumConstants());
    Set<ServiceLevelType> actual =
        Sets.newHashSet(ServiceLevel.class.getEnumConstants()).stream()
            .map(mapper::map)
            .collect(Collectors.toSet());

    assertEquals(expected, actual);
  }

  @Test
  void testOpenApiEnumValuesMatchV1() {
    ApiModelMapperV1 mapper = new ApiModelMapperV1Impl();
    Set<org.candlepin.subscriptions.utilization.api.v1.model.ServiceLevelType> expected =
        Sets.newHashSet(
            org.candlepin.subscriptions.utilization.api.v1.model.ServiceLevelType.class
                .getEnumConstants());
    Set<org.candlepin.subscriptions.utilization.api.v1.model.ServiceLevelType> actual =
        Sets.newHashSet(ServiceLevel.class.getEnumConstants()).stream()
            .map(mapper::map)
            .collect(Collectors.toSet());

    assertEquals(expected, actual);
  }
}

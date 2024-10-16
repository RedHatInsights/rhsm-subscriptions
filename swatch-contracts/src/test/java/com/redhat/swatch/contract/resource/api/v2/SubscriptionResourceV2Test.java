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
package com.redhat.swatch.contract.resource.api.v2;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.redhat.swatch.configuration.registry.ProductId;
import com.redhat.swatch.contract.openapi.model.SkuCapacityReportSortV2;
import com.redhat.swatch.contract.openapi.model.UsageType;
import io.quarkus.security.ForbiddenException;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

@QuarkusTest
class SubscriptionResourceV2Test {
  private static final ProductId RHEL_FOR_X86 = ProductId.fromString("RHEL for x86");
  private final OffsetDateTime min = OffsetDateTime.now().minusDays(4);
  private final OffsetDateTime max = OffsetDateTime.now().plusDays(4);

  @Inject SubscriptionResourceV2 subscriptionResource;
  @InjectMock SubscriptionTableControllerV2 controller;

  @TestSecurity(
      user = "123456",
      roles = {"test"})
  @Test
  void testAccessDeniedWhenUserIsNotACustomer() {
    assertThrows(
        ForbiddenException.class,
        () ->
            subscriptionResource.getSkuCapacityReportV2(
                RHEL_FOR_X86,
                0,
                10,
                null,
                null,
                UsageType.PRODUCTION,
                null,
                null,
                min,
                max,
                null,
                SkuCapacityReportSortV2.SKU,
                null));
  }

  @TestSecurity(
      user = "123456",
      roles = {"customer"})
  @Test
  void testAccessAlowed() {
    assertDoesNotThrow(
        () ->
            subscriptionResource.getSkuCapacityReportV2(
                RHEL_FOR_X86,
                0,
                10,
                null,
                null,
                UsageType.PRODUCTION,
                null,
                null,
                min,
                max,
                null,
                SkuCapacityReportSortV2.SKU,
                null));
  }
}

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
package org.candlepin.subscriptions.resource.api.v1;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.redhat.swatch.configuration.registry.ProductId;
import java.time.OffsetDateTime;
import org.candlepin.subscriptions.db.OrgConfigRepository;
import org.candlepin.subscriptions.security.WithMockRedHatPrincipal;
import org.candlepin.subscriptions.utilization.api.model.UsageType;
import org.candlepin.subscriptions.utilization.api.v1.model.SkuCapacityReportSort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles({"api", "test", "capacity-ingress"})
@WithMockRedHatPrincipal("123456")
class SubscriptionResourceV1Test {
  private static final ProductId RHEL_FOR_X86 = ProductId.fromString("RHEL for x86");
  private final OffsetDateTime min = OffsetDateTime.now().minusDays(4);
  private final OffsetDateTime max = OffsetDateTime.now().plusDays(4);

  @Autowired SubscriptionResourceV1 subscriptionResource;
  @MockBean OrgConfigRepository orgConfigRepository;

  @BeforeEach
  public void setupTests() {
    when(orgConfigRepository.existsByOrgId("owner123456")).thenReturn(true);
  }

  @Test
  @WithMockRedHatPrincipal("1111")
  void testAccessDeniedWhenAccountIsNotInAllowlist() {
    assertThrows(
        AccessDeniedException.class,
        () ->
            subscriptionResource.getSkuCapacityReport(
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
                null,
                SkuCapacityReportSort.SKU,
                null));
  }

  @Test
  @WithMockRedHatPrincipal(
      value = "123456",
      roles = {})
  void testAccessDeniedWhenUserIsNotAnAdmin() {
    assertThrows(
        AccessDeniedException.class,
        () ->
            subscriptionResource.getSkuCapacityReport(
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
                null,
                SkuCapacityReportSort.SKU,
                null));
  }
}

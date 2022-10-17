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
package org.candlepin.subscriptions.tally;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.google.common.collect.ImmutableMap;
import java.util.Optional;
import org.candlepin.subscriptions.ApplicationProperties;
import org.candlepin.subscriptions.db.AccountConfigRepository;
import org.candlepin.subscriptions.db.model.config.AccountConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles({"worker", "test"})
class TallySnapshotControllerTest {

  public static final String ACCOUNT = "foo123";
  public static final String ORG_ID = "org123";

  @Autowired TallySnapshotController controller;

  @MockBean AccountConfigRepository accountRepo;

  @MockBean CloudigradeAccountUsageCollector cloudigradeCollector;

  @MockBean MetricUsageCollector metricUsageCollector;

  @MockBean InventoryAccountUsageCollector inventoryCollector;

  @Autowired ApplicationProperties props;

  private boolean defaultCloudigradeIntegrationEnablement;

  @BeforeEach
  void setup() {
    AccountConfig accountConfig = new AccountConfig(ACCOUNT);
    accountConfig.setOrgId("ORG_" + ACCOUNT);
    when(accountRepo.findById(ACCOUNT)).thenReturn(Optional.of(accountConfig));

    defaultCloudigradeIntegrationEnablement = props.isCloudigradeEnabled();
    when(inventoryCollector.collect(any(), any()))
        .thenReturn(ImmutableMap.of(ACCOUNT, new AccountUsageCalculation(ACCOUNT)));
  }

  @AfterEach
  void restore() {
    props.setCloudigradeEnabled(defaultCloudigradeIntegrationEnablement);
  }

  @Test
  void testCloudigradeAccountUsageCollectorEnabled() throws Exception {
    props.setCloudigradeEnabled(true);
    when(accountRepo.findOrgByAccountNumber(ACCOUNT)).thenReturn(ORG_ID);
    controller.produceSnapshotsForAccount(ACCOUNT);
    verify(cloudigradeCollector).enrichUsageWithCloudigradeData(any(), any(), any());
  }

  @Test
  void testWhenCloudigradeAccountUsageCollectorEnabledAndMissingOrgId_EnrichmentNotInvoked()
      throws Exception {
    props.setCloudigradeEnabled(true);
    controller.produceSnapshotsForAccount(ACCOUNT);
    verifyNoInteractions(cloudigradeCollector);
  }

  @Test
  void testCloudigradeAccountUsageCollectorExceptionIgnored() throws Exception {
    props.setCloudigradeEnabled(true);
    when(accountRepo.findOrgByAccountNumber(ACCOUNT)).thenReturn(ORG_ID);
    doThrow(new RuntimeException())
        .when(cloudigradeCollector)
        .enrichUsageWithCloudigradeData(any(), any(), any());
    controller.produceSnapshotsForAccount(ACCOUNT);
    verify(cloudigradeCollector, times(2)).enrichUsageWithCloudigradeData(any(), any(), any());
  }

  @Test
  void testCloudigradeAccountUsageCollectorDisabled() {
    props.setCloudigradeEnabled(false);
    controller.produceSnapshotsForAccount(ACCOUNT);
    verifyNoInteractions(cloudigradeCollector);
  }
}

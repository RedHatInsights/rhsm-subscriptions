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
package org.candlepin.subscriptions.inventory.db;

import static java.time.OffsetDateTime.now;
import static java.util.Map.of;
import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.candlepin.subscriptions.inventory.db.model.InventoryHostFacts;
import org.candlepin.subscriptions.test.ExtendWithInventoryService;
import org.candlepin.subscriptions.test.ExtendWithSwatchDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@Disabled(
    value =
        "The quay inventory DB repository is private and the Jenkins instance is not logged into Quay.io")
@SpringBootTest
@ActiveProfiles(value = {"worker"})
class InventoryRepositoryIT implements ExtendWithInventoryService, ExtendWithSwatchDatabase {

  private static final String ORG_ID = "org123";
  private static final String ACCOUNT = "account";
  private static final String DISPLAY_NAME = "display name";
  private static final String REPORTER = "test";
  private static final String SUBSCRIPTION_MANAGER_ID = "subs id";
  private static final String VIRTUAL_HOST_UUID = "virtual host uuid test";
  private static final String SYSTEM_PURPOSE_ROLE = "system purpose role test";
  private static final String SYSTEM_PURPOSE_SLA = "system purpose sla test";
  private static final String SYSTEM_PURPOSE_USAGE = "system purpose usage test";
  private static final String GUEST_ID = "guess id";
  private static final String SYNC_TIMESTAMP = "sync timestamp";
  private static final String SYSPURPOSE_ROLE = "SYSPURPOSE_ROLE test";
  private static final String SYSPURPOSE_SLA = "SYSPURPOSE_SLA test";
  private static final String SYSPURPOSE_USAGE = "SYSPURPOSE_USAGE test";
  private static final String SYSPURPOSE_UNITS = "SYSPURPOSE_UNITS test";
  private static final String BILLING_MODEL = "BILLING_MODEL test";
  private static final String INFRASTRUCTURE_TYPE = "INFRASTRUCTURE_TYPE test";
  private static final int CORES_PER_SOCKET = 4;
  private static final int NUMBER_OF_SOCKETS = 5;
  private static final int NUMBER_OF_CPUS = 8;
  private static final int THREADS_PER_CORE = 2;
  private static final String CLOUD_PROVIDER = "CLOUD_PROVIDER test";
  private static final String ARCH = "ARCH test";
  private static final String INSIGHTS_ID = "INSIGHTS_ID test";
  private static final String PROVIDER_ID = "provider ID test";
  private static final Set<String> RH_PROD = Set.of("a1", "a2", "a3");
  private static final Set<String> RH_PRODUCTS_INSTALLED = Set.of("b1", "b2", "b3");
  private static final Set<Map<String, String>> INSTALLED_PRODUCTS =
      Set.of(Map.of("id", "d1"), Map.of("id", "d2"));
  private static final int CUT_OFF_DAYS = 3;

  @Autowired InventoryRepository repository;

  @AfterEach
  void tearDown() {
    deleteAllHostsFromInventoryDatabase();
  }

  @Transactional
  @Test
  void testStreamFacts() { // NOSONAR
    assertEquals(0, repository.streamFacts(ORG_ID, CUT_OFF_DAYS).count());

    UUID expectedHostId = givenHost();
    List<InventoryHostFacts> facts = repository.streamFacts(ORG_ID, CUT_OFF_DAYS).toList();
    assertEquals(1, facts.size());
    InventoryHostFacts fact = facts.get(0);
    assertEquals(expectedHostId, fact.getInventoryId());
    assertEquals(ORG_ID, fact.getOrgId());
    assertNotNull(fact.getModifiedOn());
    assertTrue(fact.isVirtual());
    assertEquals(VIRTUAL_HOST_UUID, fact.getHypervisorUuid());
    assertEquals(VIRTUAL_HOST_UUID, fact.getSatelliteHypervisorUuid());
    assertEquals(SYSTEM_PURPOSE_ROLE, fact.getSatelliteRole());
    assertEquals(SYSTEM_PURPOSE_SLA, fact.getSatelliteSla());
    assertEquals(SYSTEM_PURPOSE_USAGE, fact.getSatelliteUsage());
    assertEquals(GUEST_ID, fact.getGuestId());
    assertEquals(SYNC_TIMESTAMP, fact.getSyncTimestamp());
    assertEquals(SYSPURPOSE_ROLE, fact.getSyspurposeRole());
    assertEquals(SYSPURPOSE_SLA, fact.getSyspurposeSla());
    assertEquals(SYSPURPOSE_USAGE, fact.getSyspurposeUsage());
    assertEquals(SYSPURPOSE_UNITS, fact.getSyspurposeUnits());
    assertEquals(BILLING_MODEL, fact.getBillingModel());
    assertEquals(INFRASTRUCTURE_TYPE, fact.getSystemProfileInfrastructureType());
    assertEquals(CORES_PER_SOCKET, fact.getSystemProfileCoresPerSocket());
    assertEquals(NUMBER_OF_SOCKETS, fact.getSystemProfileSockets());
    assertEquals(NUMBER_OF_CPUS, fact.getSystemProfileCpus());
    assertEquals(THREADS_PER_CORE, fact.getSystemProfileThreadsPerCore());
    assertEquals(CLOUD_PROVIDER, fact.getCloudProvider());
    assertEquals(ARCH, fact.getSystemProfileArch());
    assertTrue(fact.isMarketplace());
    assertEquals(INSIGHTS_ID, fact.getInsightsId());
    assertEquals(PROVIDER_ID, fact.getInstanceId());
    assertEquals(VIRTUAL_HOST_UUID, fact.getHardwareSubmanId());
    assertEquals(RH_PROD, fact.getProducts());
    assertEquals(RH_PRODUCTS_INSTALLED, fact.getQpcProducts());
    assertEquals(Set.of("d1", "d2"), fact.getSystemProfileProductIds());
  }

  @Test
  void testActiveSystemCount() {
    assertEquals(0, repository.activeSystemCountForOrgId(ORG_ID, CUT_OFF_DAYS));

    givenHost();
    assertEquals(1, repository.activeSystemCountForOrgId(ORG_ID, CUT_OFF_DAYS));
  }

  @Transactional
  @Test
  void testGetReportedHypervisors() {
    assertEquals(0, repository.getReportedHypervisors(List.of(ORG_ID)).count());

    givenHost();
    List<Object[]> reportedHypervisors =
        repository.getReportedHypervisors(List.of(ORG_ID)).toList();
    assertEquals(2, reportedHypervisors.size());
    assertEquals(VIRTUAL_HOST_UUID, reportedHypervisors.get(0)[0]);
    assertEquals(VIRTUAL_HOST_UUID, reportedHypervisors.get(1)[0]);
  }

  @Transactional
  @Test
  void testStreamActiveSubscriptionManagerIds() {
    assertEquals(0, repository.streamActiveSubscriptionManagerIds(ORG_ID, CUT_OFF_DAYS).count());

    givenHost();
    List<String> actual =
        repository.streamActiveSubscriptionManagerIds(ORG_ID, CUT_OFF_DAYS).toList();
    assertEquals(1, actual.size());
    assertEquals(SUBSCRIPTION_MANAGER_ID, actual.get(0));
  }

  private UUID givenHost() {
    UUID hostId = randomUUID();
    createHostInInventoryDatabase(
        hostId,
        ORG_ID,
        ACCOUNT,
        DISPLAY_NAME,
        of(
            "rhsm",
            of(
                "IS_VIRTUAL",
                "true",
                "GUEST_ID",
                GUEST_ID,
                "SYNC_TIMESTAMP",
                SYNC_TIMESTAMP,
                "SYSPURPOSE_ROLE",
                SYSPURPOSE_ROLE,
                "SYSPURPOSE_SLA",
                SYSPURPOSE_SLA,
                "SYSPURPOSE_USAGE",
                SYSPURPOSE_USAGE,
                "SYSPURPOSE_UNITS",
                SYSPURPOSE_UNITS,
                "BILLING_MODEL",
                BILLING_MODEL,
                "RH_PROD",
                RH_PROD),
            "satellite",
            of(
                "virtual_host_uuid",
                VIRTUAL_HOST_UUID,
                "system_purpose_role",
                SYSTEM_PURPOSE_ROLE,
                "system_purpose_sla",
                SYSTEM_PURPOSE_SLA,
                "system_purpose_usage",
                SYSTEM_PURPOSE_USAGE),
            "qpc",
            of("IS_RHEL", "true", "rh_products_installed", RH_PRODUCTS_INSTALLED)),
        of(
            "subscription_manager_id",
            SUBSCRIPTION_MANAGER_ID,
            "insights_id",
            INSIGHTS_ID,
            "provider_id",
            PROVIDER_ID),
        of(
            "infrastructure_type",
            INFRASTRUCTURE_TYPE,
            "cores_per_socket",
            CORES_PER_SOCKET,
            "number_of_sockets",
            NUMBER_OF_SOCKETS,
            "number_of_cpus",
            NUMBER_OF_CPUS,
            "threads_per_core",
            THREADS_PER_CORE,
            "cloud_provider",
            CLOUD_PROVIDER,
            "arch",
            ARCH,
            "is_marketplace",
            "true",
            "installed_products",
            INSTALLED_PRODUCTS,
            "virtual_host_uuid",
            VIRTUAL_HOST_UUID),
        REPORTER,
        now(),
        now(),
        now());

    return hostId;
  }
}

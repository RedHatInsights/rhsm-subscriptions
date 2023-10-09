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
package org.candlepin.subscriptions.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.util.TimeZone;
import org.candlepin.subscriptions.db.OrgConfigRepository;
import org.candlepin.subscriptions.db.model.config.OptInType;
import org.candlepin.subscriptions.db.model.config.OrgConfig;
import org.candlepin.subscriptions.test.TestClockConfiguration;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.candlepin.subscriptions.utilization.api.model.OptInConfig;
import org.candlepin.subscriptions.utilization.api.model.OptInConfigDataOrg;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@SpringBootTest
@ActiveProfiles("test")
@Import(TestClockConfiguration.class)
class OptInControllerTest {

  @Autowired private OrgConfigRepository orgRepo;

  @Autowired private ApplicationClock clock;

  private OptInController controller;

  @BeforeEach
  void setupTest() {
    TimeZone.setDefault(TimeZone.getTimeZone(clock.getClock().getZone()));
    controller = new OptInController(clock, orgRepo);
  }

  @Test
  void testOptInWithNewEntity() {
    String expectedOrg = "my-org";

    OptInConfig saved = controller.optIn("my-org", OptInType.API);
    assertNotNull(saved);
    assertNotNull(saved.getData());
    assertNotNull(saved.getMeta());
    assertTrue(saved.getData().getOptInComplete());
    assertNotNull(saved.getData().getOrg());

    assertEquals(expectedOrg, saved.getMeta().getOrgId());

    OptInConfigDataOrg orgConfig = saved.getData().getOrg();
    assertNotNull(orgConfig);
    assertEquals(expectedOrg, orgConfig.getOrgId());
    assertEquals(clock.now(), orgConfig.getCreated());
    assertEquals(clock.now(), orgConfig.getLastUpdated());
    assertEquals(OptInType.API.name(), orgConfig.getOptInType());
  }

  @Test
  void testOptInWithExistingEntity() {
    OrgConfig existingOrgConfig = setupExistingOrgConfig("TEST_ORG1");

    OptInConfig modified = controller.optIn(existingOrgConfig.getOrgId(), OptInType.API);

    assertNotNull(modified);
    assertNotNull(modified.getData());
    assertNotNull(modified.getMeta());
    assertTrue(modified.getData().getOptInComplete());
    assertNotNull(modified.getData().getOrg());
    assertEquals(existingOrgConfig.getOrgId(), modified.getMeta().getOrgId());

    OptInConfigDataOrg orgConfig = modified.getData().getOrg();
    assertNotNull(orgConfig);
    assertEquals(existingOrgConfig.getOrgId(), orgConfig.getOrgId());
    assertEquals(clock.now().minusDays(1), orgConfig.getCreated());
    assertEquals(clock.now(), orgConfig.getLastUpdated());
    assertEquals(OptInType.DB.name(), orgConfig.getOptInType());
  }

  @Test
  void testOptInCreatesAccountConfigIfItDoesntExist() {
    OrgConfig existingOrgConfig = setupExistingOrgConfig("TEST_ORG3");

    OptInConfig saved = controller.optIn(existingOrgConfig.getOrgId(), OptInType.API);

    assertNotNull(saved);
    assertNotNull(saved.getData());
    assertNotNull(saved.getMeta());
    assertTrue(saved.getData().getOptInComplete());
    assertNotNull(saved.getData().getOrg());
    assertEquals(existingOrgConfig.getOrgId(), saved.getMeta().getOrgId());

    OptInConfigDataOrg savedOrgConfig = saved.getData().getOrg();
    assertEquals(existingOrgConfig.getOrgId(), savedOrgConfig.getOrgId());
    assertEquals(clock.now().minusDays(1), savedOrgConfig.getCreated());
    assertEquals(clock.now(), savedOrgConfig.getLastUpdated());
    // OptInType expected to be API since the config didn't exist yet.
    assertEquals(OptInType.DB.name(), savedOrgConfig.getOptInType());
  }

  @Test
  void testOptInViaOrgIdOnly() {
    controller.optInByOrgId("org123", OptInType.API);
    assertTrue(orgRepo.existsByOrgId("org123"));
  }

  @Test
  void testOptInViaOrgIdDoesNotUseApiIfOptInExists() {
    OrgConfig orgConfig = new OrgConfig("org123");
    orgConfig.setOptInType(OptInType.API);
    orgConfig.setCreated(OffsetDateTime.parse("2021-04-06T00:00:00Z"));
    orgConfig.setUpdated(OffsetDateTime.parse("2021-04-06T00:00:00Z"));
    orgRepo.save(orgConfig);

    OptInController controllerWithMockApi = new OptInController(clock, orgRepo);
    controllerWithMockApi.optInByOrgId("org123", OptInType.API);

    assertTrue(orgRepo.existsById("org123"));
  }

  @Test
  void testOptOut() {
    String expectedOrgId = "my-org";

    controller.optOut(expectedOrgId);
    assertTrue(orgRepo.findById(expectedOrgId).isEmpty());
  }

  @Test
  void testGetOptInConfig() {
    String expectedOrg = "owner123456";
    OptInType expectedOptIn = OptInType.API;
    OffsetDateTime expectedOptInDate = clock.now();
    OffsetDateTime expectedUpdatedDate = expectedOptInDate.plusDays(1);

    OrgConfig orgConfig = new OrgConfig(expectedOrg);
    orgConfig.setOptInType(expectedOptIn);
    orgConfig.setCreated(expectedOptInDate);
    orgConfig.setUpdated(expectedUpdatedDate);
    orgRepo.save(orgConfig);

    OptInConfig dto = controller.getOptInConfig(expectedOrg);
    assertNotNull(dto.getData());
    assertNotNull(dto.getMeta());
    assertNotNull(dto.getData().getOrg());
    assertEquals(expectedOrg, dto.getMeta().getOrgId());

    OptInConfigDataOrg orgDto = dto.getData().getOrg();
    assertEquals(orgDto.getOrgId(), expectedOrg);
    assertEquals(orgDto.getOptInType(), expectedOptIn.name());
    assertEquals(orgDto.getCreated(), expectedOptInDate);
    assertEquals(orgDto.getLastUpdated(), expectedUpdatedDate);

    assertTrue(dto.getData().getOptInComplete());
  }

  @Test
  void testGetOptInConfigForOrgId() {

    OrgConfig orgConfig = new OrgConfig("org123");
    orgConfig.setOptInType(OptInType.API);
    orgConfig.setCreated(OffsetDateTime.parse("2021-04-06T00:00:00Z"));
    orgConfig.setUpdated(OffsetDateTime.parse("2021-04-06T00:00:00Z"));
    orgRepo.save(orgConfig);

    OptInConfig dto = controller.getOptInConfigForOrgId("org123");
    assertNotNull(dto.getData().getOrg());
    assertEquals("org123", dto.getData().getOrg().getOrgId());
  }

  private OrgConfig setupExistingOrgConfig(String org) {
    OrgConfig config = new OrgConfig(org);
    config.setCreated(clock.now().minusDays(1));
    config.setUpdated(clock.now());
    config.setOptInType(OptInType.DB);
    return orgRepo.save(config);
  }
}

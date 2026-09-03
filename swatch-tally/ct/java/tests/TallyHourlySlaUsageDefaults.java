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
package tests;

import static com.redhat.swatch.component.tests.utils.Topics.SWATCH_SERVICE_INSTANCE_INGRESS;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static utils.TallyTestProducts.RHEL_FOR_X86_ELS_PAYG;

import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.utils.RandomUtils;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.candlepin.subscriptions.json.Event;
import org.junit.jupiter.api.Test;

/**
 * Component tests for hourly tally with SLA/Usage defaults.
 *
 * <p>Tests the flow:
 *
 * <ol>
 *   <li>Ingest PAYG events with and without SLA/Usage specified
 *   <li>Run hourly tally (processes events from Kafka)
 *   <li>Verify tally snapshots reflect correct defaults or explicit values
 * </ol>
 */
public class TallyHourlySlaUsageDefaults extends BaseTallyComponentTest {

  @Test
  @TestPlanName("tally-hourly-sla-usage-defaults-TC001")
  void testHourlyTallyDefaultsToPremiumProductionWhenSlaUsageNotSet() {
    // Given: Org is set up and feature flag is enabled
    String productTag = RHEL_FOR_X86_ELS_PAYG.productTag();
    String metricId = RHEL_FOR_X86_ELS_PAYG.metricIds().get(1);
    givenFeatureFlagIsConfigured(true);

    // Given: A PAYG event with NO SLA/Usage specified
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    String instanceId = RandomUtils.generateRandom();
    String eventId = UUID.randomUUID().toString();

    // Create event with explicit defaults, then clear SLA/Usage
    Event event =
        helpers.createPaygEventWithTimestamp(
            orgId,
            instanceId,
            now.minusHours(1).toString(),
            eventId,
            metricId,
            2.0f,
            RHEL_FOR_X86_ELS_PAYG.productId(),
            productTag);
    event.setSla(null); // Clear SLA - should default to PREMIUM
    event.setUsage(null); // Clear Usage - should default to PRODUCTION

    // When: Event is ingested
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event);

    // When: Hourly tally runs
    service.performHourlyTallyForOrg(orgId);

    // Then: Tally report with Premium SLA filter should have data
    OffsetDateTime beginning = now.minusHours(2);
    OffsetDateTime ending = now.plusHours(1);

    var premiumReport =
        service.getTallyReportData(
            orgId,
            productTag,
            metricId,
            Map.of(
                "granularity",
                "Hourly",
                "beginning",
                beginning.toString(),
                "ending",
                ending.toString(),
                "sla",
                "Premium"));

    assertNotNull(premiumReport, "Tally report with Premium SLA should exist");
    assertNotNull(premiumReport.getData(), "Premium report should have data points");
    assertFalse(premiumReport.getData().isEmpty(), "Premium report should have data points");
    assertTrue(
        premiumReport.getData().stream().anyMatch(dp -> Boolean.TRUE.equals(dp.getHasData())),
        "Premium report should have at least one data point with data");

    // Then: Tally report with Production Usage filter should have data
    var productionReport =
        service.getTallyReportData(
            orgId,
            productTag,
            metricId,
            Map.of(
                "granularity",
                "Hourly",
                "beginning",
                beginning.toString(),
                "ending",
                ending.toString(),
                "usage",
                "Production"));

    assertNotNull(productionReport, "Tally report with Production Usage should exist");
    assertNotNull(productionReport.getData(), "Production report should have data points");
    assertFalse(productionReport.getData().isEmpty(), "Production report should have data points");
    assertTrue(
        productionReport.getData().stream().anyMatch(dp -> Boolean.TRUE.equals(dp.getHasData())),
        "Production report should have at least one data point with data");

    // Then: Tally report with Standard SLA filter should have no data (host defaulted to Premium)
    var standardReport =
        service.getTallyReportData(
            orgId,
            productTag,
            metricId,
            Map.of(
                "granularity",
                "Hourly",
                "beginning",
                beginning.toString(),
                "ending",
                ending.toString(),
                "sla",
                "Standard"));

    assertNotNull(standardReport, "Tally report with Standard SLA should exist");
    assertTrue(
        standardReport.getData().stream().noneMatch(dp -> Boolean.TRUE.equals(dp.getHasData())),
        "Standard SLA report should have no data (event defaulted to Premium)");
  }

  @Test
  @TestPlanName("tally-hourly-sla-usage-defaults-TC002")
  void testHourlyTallyRespectsExplicitSlaUsage() {
    // Given: Org is set up and feature flag is enabled
    String productTag = RHEL_FOR_X86_ELS_PAYG.productTag();
    String metricId = RHEL_FOR_X86_ELS_PAYG.metricIds().get(1);
    givenFeatureFlagIsConfigured(true);

    // Given: A PAYG event with explicit SLA=Standard and Usage=Development/Test
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    String instanceId = RandomUtils.generateRandom();
    String eventId = UUID.randomUUID().toString();

    Event event =
        helpers.createPaygEventWithTimestamp(
            orgId,
            instanceId,
            now.minusHours(1).toString(),
            eventId,
            metricId,
            2.0f,
            Event.Sla.STANDARD,
            Event.Usage.DEVELOPMENT_TEST,
            Event.BillingProvider.AWS,
            RandomUtils.generateRandom(),
            Event.HardwareType.CLOUD,
            RHEL_FOR_X86_ELS_PAYG.productId(),
            productTag);

    // When: Event is ingested
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event);

    // When: Hourly tally runs
    service.performHourlyTallyForOrg(orgId);

    // Then: Tally report with Standard SLA filter should have data
    OffsetDateTime beginning = now.minusHours(2);
    OffsetDateTime ending = now.plusHours(1);

    var standardReport =
        service.getTallyReportData(
            orgId,
            productTag,
            metricId,
            Map.of(
                "granularity",
                "Hourly",
                "beginning",
                beginning.toString(),
                "ending",
                ending.toString(),
                "sla",
                "Standard"));

    assertNotNull(standardReport, "Tally report with Standard SLA should exist");
    assertNotNull(standardReport.getData(), "Standard report should have data points");
    assertFalse(standardReport.getData().isEmpty(), "Standard report should have data points");
    assertTrue(
        standardReport.getData().stream().anyMatch(dp -> Boolean.TRUE.equals(dp.getHasData())),
        "Standard report should have at least one data point with data");

    // Then: Tally report with Development/Test Usage filter should have data
    var devTestReport =
        service.getTallyReportData(
            orgId,
            productTag,
            metricId,
            Map.of(
                "granularity",
                "Hourly",
                "beginning",
                beginning.toString(),
                "ending",
                ending.toString(),
                "usage",
                "Development/Test"));

    assertNotNull(devTestReport, "Tally report with Development/Test Usage should exist");
    assertNotNull(devTestReport.getData(), "Development/Test report should have data points");
    assertFalse(
        devTestReport.getData().isEmpty(), "Development/Test report should have data points");
    assertTrue(
        devTestReport.getData().stream().anyMatch(dp -> Boolean.TRUE.equals(dp.getHasData())),
        "Development/Test report should have at least one data point with data");

    // Then: Tally report with Premium SLA filter should have no data (event has Standard)
    var premiumReport =
        service.getTallyReportData(
            orgId,
            productTag,
            metricId,
            Map.of(
                "granularity",
                "Hourly",
                "beginning",
                beginning.toString(),
                "ending",
                ending.toString(),
                "sla",
                "Premium"));

    assertNotNull(premiumReport, "Tally report with Premium SLA should exist");
    assertTrue(
        premiumReport.getData().stream().noneMatch(dp -> Boolean.TRUE.equals(dp.getHasData())),
        "Premium SLA report should have no data (event has Standard)");

    // Then: Tally report with Production Usage filter should have no data (event has
    // Development/Test)
    var productionReport =
        service.getTallyReportData(
            orgId,
            productTag,
            metricId,
            Map.of(
                "granularity",
                "Hourly",
                "beginning",
                beginning.toString(),
                "ending",
                ending.toString(),
                "usage",
                "Production"));

    assertNotNull(productionReport, "Tally report with Production Usage should exist");
    assertTrue(
        productionReport.getData().stream().noneMatch(dp -> Boolean.TRUE.equals(dp.getHasData())),
        "Production Usage report should have no data (event has Development/Test)");
  }
}

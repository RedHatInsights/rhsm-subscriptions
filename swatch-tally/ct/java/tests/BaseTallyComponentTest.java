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

import static com.redhat.swatch.component.tests.utils.Topics.TALLY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import api.TallySwatchService;
import api.TallyUnleashService;
import api.TallyWiremockService;
import com.redhat.swatch.component.tests.api.ComponentTest;
import com.redhat.swatch.component.tests.api.KafkaBridge;
import com.redhat.swatch.component.tests.api.KafkaBridgeService;
import com.redhat.swatch.component.tests.api.SpringBoot;
import com.redhat.swatch.component.tests.api.SwatchDatabase;
import com.redhat.swatch.component.tests.api.Unleash;
import com.redhat.swatch.component.tests.api.Wiremock;
import com.redhat.swatch.component.tests.api.db.DatabaseService;
import com.redhat.swatch.component.tests.utils.AwaitilitySettings;
import com.redhat.swatch.component.tests.utils.AwaitilityUtils;
import com.redhat.swatch.component.tests.utils.RandomUtils;
import com.redhat.swatch.tally.test.model.InstanceData;
import com.redhat.swatch.tally.test.model.InstanceResponse;
import com.redhat.swatch.tally.test.model.TallyReportData;
import com.redhat.swatch.tally.test.model.TallyReportDataPoint;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import utils.TallyDbHostSeeder;
import utils.TallyTestHelpers;

/**
 * Base class for swatch-tally component tests.
 *
 * <p>Provides common setup and infrastructure for all tally component tests:
 *
 * <ul>
 *   <li>Static service instances - Configured with appropriate framework annotations
 *   <li>Helper utilities - Common test data creation and verification methods
 *   <li>Test setup - Generates unique organization ID for each test
 * </ul>
 *
 * <p>Test classes should extend this base class and use the provided service instances and helpers.
 */
@ComponentTest(name = "swatch-tally")
public class BaseTallyComponentTest {

  // --- Static helper utilities ---

  static final TallyTestHelpers helpers = new TallyTestHelpers();

  // --- Static service instances ---

  @Wiremock static TallyWiremockService wiremock = new TallyWiremockService();

  @KafkaBridge
  static KafkaBridgeService kafkaBridge = new KafkaBridgeService().subscribeToTopic(TALLY);

  @SpringBoot(service = "swatch-tally")
  static TallySwatchService service = new TallySwatchService();

  @Unleash static TallyUnleashService unleash = new TallyUnleashService(service);

  @SwatchDatabase static DatabaseService swatchDatabase = new DatabaseService();

  // --- Instance fields ---

  protected final TallyDbHostSeeder seeder = new TallyDbHostSeeder(swatchDatabase);
  protected String orgId;

  @BeforeEach
  void setUp() {
    orgId = RandomUtils.generateRandom();
  }

  // --- Protected helper methods ---

  protected void givenFeatureFlagIsConfigured(boolean enablePrimaryRowSearches) {
    if (enablePrimaryRowSearches) {
      unleash.enablePrimaryRowSearches();
    } else {
      unleash.disablePrimaryRowSearches();
    }
  }

  // --- Shared tally report query helpers ---

  protected double getHourlyTallySum(
      String orgId,
      String productTag,
      String metricId,
      OffsetDateTime beginning,
      OffsetDateTime ending) {
    Map<String, ?> queryParams =
        Map.of(
            "granularity", "Hourly",
            "beginning", beginning.toString(),
            "ending", ending.toString());

    TallyReportData resp = service.getTallyReportData(orgId, productTag, metricId, queryParams);
    if (resp.getData() == null) {
      return 0.0;
    }

    return resp.getData().stream()
        .collect(Collectors.summarizingInt(TallyReportDataPoint::getValue))
        .getSum();
  }

  /**
   * Waits for the hourly tally sum to reach the expected value, retrying with tally runs.
   *
   * @param orgId the organization ID
   * @param productTag the product tag
   * @param metricId the metric ID
   * @param beginning the start of the time range
   * @param ending the end of the time range
   * @param expected the expected tally sum
   * @return the final tally sum
   */
  protected double awaitHourlyTallySum(
      String orgId,
      String productTag,
      String metricId,
      OffsetDateTime beginning,
      OffsetDateTime ending,
      double expected) {
    AwaitilitySettings settings =
        AwaitilitySettings.using(Duration.ofSeconds(1), Duration.ofSeconds(30))
            .withService(service)
            .timeoutMessage(
                "Timed out waiting for hourly tally to reach expected value %.4f (product=%s metric=%s)",
                expected, productTag, metricId);

    AwaitilityUtils.untilAsserted(
        () -> {
          service.performHourlyTallyForOrg(orgId);
          assertEquals(
              expected,
              getHourlyTallySum(orgId, productTag, metricId, beginning, ending),
              "Hourly tally sum should match expected value");
        },
        settings);

    return getHourlyTallySum(orgId, productTag, metricId, beginning, ending);
  }

  /**
   * Retrieves a single instance matching a display name substring for a product within a time
   * range.
   *
   * @param orgId the organization ID
   * @param productTag the product tag
   * @param beginning the start of the time range
   * @param ending the end of the time range
   * @param displayNameContains the substring to match in display names
   * @return the matching instance, or null if not found
   */
  protected InstanceData getInstanceByDisplayName(
      String orgId,
      String productTag,
      OffsetDateTime beginning,
      OffsetDateTime ending,
      String displayNameContains) {
    InstanceResponse response = service.getInstancesByProduct(orgId, productTag, beginning, ending);
    if (response.getData() == null) {
      return null;
    }

    return response.getData().stream()
        .filter(instance -> instance.getDisplayName() != null)
        .filter(instance -> instance.getDisplayName().contains(displayNameContains))
        .findFirst()
        .orElse(null);
  }

  /**
   * Counts instances matching a display name substring for a product within a time range.
   *
   * @param orgId the organization ID
   * @param productTag the product tag
   * @param beginning the start of the time range
   * @param ending the end of the time range
   * @param displayNameContains the substring to match in display names
   * @return the count of matching instances
   */
  protected long getInstancesCountByDisplayName(
      String orgId,
      String productTag,
      OffsetDateTime beginning,
      OffsetDateTime ending,
      String displayNameContains) {
    InstanceResponse response = service.getInstancesByProduct(orgId, productTag, beginning, ending);
    if (response.getData() == null) {
      return 0;
    }

    return response.getData().stream()
        .map(InstanceData::getDisplayName)
        .filter(Objects::nonNull)
        .filter(d -> d.contains(displayNameContains))
        .count();
  }

  /**
   * Asserts that an instance has the expected measurements for a product.
   *
   * <p>This method verifies:
   *
   * <ul>
   *   <li>The instance exists in the response
   *   <li>The actual measurements match the expected measurements (both metric IDs and values)
   *   <li>No unexpected measurements are present
   * </ul>
   *
   * @param orgId the organization ID
   * @param instanceId the instance ID to verify
   * @param productTag the product tag
   * @param beginning the start of the time range
   * @param ending the end of the time range
   * @param expectedMeasurements map of metric ID to expected value
   */
  protected void assertInstanceMeasurements(
      String orgId,
      String instanceId,
      String productTag,
      OffsetDateTime beginning,
      OffsetDateTime ending,
      Map<String, Double> expectedMeasurements) {
    InstanceResponse response = service.getInstancesByProduct(orgId, productTag, beginning, ending);
    assertNotNull(response.getData(), "Instance response data should not be null");
    assertNotNull(
        response.getMeta(), "Instance response meta should not be null for product " + productTag);
    assertNotNull(
        response.getMeta().getMeasurements(),
        "Instance response meta measurements should not be null for product " + productTag);

    List<InstanceData> instances =
        response.getData().stream()
            .filter(nextInstance -> instanceId.equals(nextInstance.getInstanceId()))
            .toList();
    assertEquals(
        1,
        instances.size(),
        "Expected only 1 instance matching instanceId="
            + instanceId
            + " but found "
            + instances.size());

    // Build actual measurements map from meta.measurements (metric IDs) and instance.measurements
    // (values)
    InstanceData instance = instances.get(0);
    List<String> metricIds = response.getMeta().getMeasurements();
    List<Double> values = instance.getMeasurements();

    assertNotNull(values, "Instance measurements should not be null");
    assertEquals(
        metricIds.size(),
        values.size(),
        String.format(
            "Number of metric IDs (%d) should match number of values (%d). MetricIds=%s, Values=%s",
            metricIds.size(), values.size(), metricIds, values));

    Map<String, Double> actualMeasurements = new HashMap<>();
    for (int i = 0; i < metricIds.size(); i++) {
      actualMeasurements.put(metricIds.get(i), values.get(i));
    }

    assertEquals(
        expectedMeasurements,
        actualMeasurements,
        String.format(
            "Instance measurements should match expected. Expected: %s, Actual: %s",
            expectedMeasurements, actualMeasurements));
  }
}

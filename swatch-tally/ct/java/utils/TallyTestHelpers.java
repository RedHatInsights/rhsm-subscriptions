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
package utils;

import com.redhat.swatch.component.tests.api.SwatchService;
import com.redhat.swatch.component.tests.logging.Log;
import io.restassured.response.Response;
import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.candlepin.subscriptions.json.Event;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

public class TallyTestHelpers {

  // Database configuration - loaded from test.properties
  private static final String DB_HOST;
  private static final int DB_PORT;
  private static final String DB_NAME;
  private static final String DB_USER;
  private static final String DB_PASSWORD;

  // Test configuration constants
  private static final String TEST_PSK = "placeholder";
  private static final String DEFAULT_BILLING_ACCOUNT = "746157280291";
  private static final String TEST_PRODUCT_ID = "204";
  private static final String DEFAULT_PRODUCT_TAG = "rhel-for-x86-els-payg";
  private static final int EVENT_EXPIRATION_DAYS = 25;

  private final NamedParameterJdbcTemplate jdbcTemplate;

  // Static initializer to load database properties from test.properties
  static {
    Properties properties = new Properties();
    try (InputStream input =
        TallyTestHelpers.class.getClassLoader().getResourceAsStream("test.properties")) {
      if (input == null) {
        throw new RuntimeException("Unable to find test.properties in classpath");
      }
      properties.load(input);

      // Load database configuration
      DB_HOST = properties.getProperty("db.host", "localhost");
      DB_PORT = Integer.parseInt(properties.getProperty("db.port", "5432"));
      DB_NAME = properties.getProperty("db.name", "rhsm-subscriptions");
      DB_USER = properties.getProperty("db.username", "rhsm-subscriptions");
      DB_PASSWORD = properties.getProperty("db.password", "rhsm-subscriptions");

      Log.info(
          "Loaded database configuration from test.properties - DB Host: %s, DB Port: %d",
          DB_HOST, DB_PORT);
    } catch (IOException e) {
      throw new RuntimeException("Failed to load test.properties", e);
    }
  }

  /**
   * Default constructor that initializes the JDBC template with default database connection
   * settings.
   */
  public TallyTestHelpers() {
    this.jdbcTemplate = new NamedParameterJdbcTemplate(createDataSource());
  }

  /**
   * Constructor that accepts a pre-configured NamedParameterJdbcTemplate for dependency injection.
   *
   * @param jdbcTemplate The JDBC template to use for database operations
   */
  public TallyTestHelpers(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /**
   * Creates a DataSource for connecting to the PostgreSQL database. This method can be overridden
   * in tests to provide custom connection settings.
   *
   * @return A configured DataSource
   */
  private DataSource createDataSource() {
    DriverManagerDataSource dataSource = new DriverManagerDataSource();
    dataSource.setUrl(String.format("jdbc:postgresql://%s:%d/%s", DB_HOST, DB_PORT, DB_NAME));
    dataSource.setUsername(DB_USER);
    dataSource.setPassword(DB_PASSWORD);
    return dataSource;
  }

  public Event createEventWithTimestamp(
      String orgId, String instanceId, String timestampStr, String eventIdStr, float value) {

    OffsetDateTime timestamp = OffsetDateTime.parse(timestampStr);
    OffsetDateTime expiration = timestamp.plusDays(EVENT_EXPIRATION_DAYS);

    Event event = new Event();
    event.setEventId(UUID.fromString(eventIdStr));
    event.setOrgId(orgId);
    event.setInstanceId(instanceId);
    event.setDisplayName(Optional.of("Test Instance"));
    event.setTimestamp(timestamp);
    event.setRecordDate(timestamp);
    event.setExpiration(Optional.of(expiration));
    event.setEventSource("cost-management");
    event.setEventType("snapshot");
    event.setSla(Event.Sla.PREMIUM);
    event.setRole(Event.Role.RED_HAT_ENTERPRISE_LINUX_SERVER);
    event.setUsage(Event.Usage.PRODUCTION);
    event.setServiceType("RHEL System");
    event.setHardwareType(Event.HardwareType.CLOUD);
    event.setCloudProvider(Event.CloudProvider.AWS);
    event.setBillingProvider(Event.BillingProvider.AWS);
    event.setBillingAccountId(Optional.of(DEFAULT_BILLING_ACCOUNT));
    event.setConversion(true);
    event.setProductIds(List.of(TEST_PRODUCT_ID));
    event.setProductTag(Set.of(DEFAULT_PRODUCT_TAG));

    var measurement = new org.candlepin.subscriptions.json.Measurement();
    measurement.setValue((double) value);
    measurement.setMetricId("VCPUS");
    event.setMeasurements(List.of(measurement));

    return event;
  }

  public void syncTallyNightly(String orgId, SwatchService service) throws Exception {
    Response response =
        service
            .given()
            .header("x-rh-swatch-psk", TEST_PSK)
            .header("x-rh-swatch-synchronous-request", "true")
            .put("/api/rhsm-subscriptions/v1/internal/rpc/tally/snapshots/" + orgId)
            .then()
            .extract()
            .response();

    if (response.getStatusCode() != 200) {
      throw new RuntimeException(
          "Tally sync failed with status code: "
              + response.getStatusCode()
              + ", response body: "
              + response.getBody().asString());
    }

    Log.info("Sync nightly tally endpoint called successfully for org: %s", orgId);
  }

  public void syncTallyHourly(String orgId, SwatchService service) throws Exception {
    Response response =
        service
            .given()
            .header("x-rh-swatch-psk", TEST_PSK)
            .queryParam("org", orgId)
            .post("/api/rhsm-subscriptions/v1/internal/tally/hourly")
            .then()
            .extract()
            .response();

    if (response.getStatusCode() != 204) {
      throw new RuntimeException(
          "Hourly tally sync failed with status code: "
              + response.getStatusCode()
              + ", response body: "
              + response.getBody().asString());
    }

    Log.info("Hourly tally endpoint called successfully for org: %s", orgId);
  }

  /**
   * Create a mock host in the database for testing purposes.
   *
   * @param orgId Organization ID
   * @param instanceId Instance ID
   * @return The UUID of the created host
   */
  public UUID createMockHost(String orgId, String instanceId) {
    Map<String, Object> accountServiceParams = new HashMap<>();
    accountServiceParams.put("orgId", orgId);

    jdbcTemplate.update(
        "INSERT INTO account_services (org_id, service_type) "
            + "VALUES (:orgId, 'HBI_HOST') "
            + "ON CONFLICT (org_id, service_type) DO NOTHING",
        accountServiceParams);

    UUID hostId = UUID.randomUUID();

    Map<String, Object> hostParams = new HashMap<>();
    hostParams.put("hostId", hostId);
    hostParams.put("instanceId", instanceId);
    hostParams.put("orgId", orgId);
    hostParams.put("billingAccountId", DEFAULT_BILLING_ACCOUNT);

    jdbcTemplate.update(
        "INSERT INTO hosts (id, org_id, display_name, insights_id, subscription_manager_id, "
            + "instance_id, billing_provider, billing_account_id, cloud_provider, last_seen, "
            + "is_guest, is_unmapped_guest, is_hypervisor, instance_type) "
            + "VALUES (:hostId, :orgId, 'test-host', CONCAT('insights-', :instanceId), "
            + "CONCAT('sm-', :instanceId), :instanceId, 'aws', :billingAccountId, 'aws', "
            + "NOW() + INTERVAL '1 day', false, false, false, 'HBI_HOST')",
        hostParams);

    Log.info(
        "Mock host created successfully for org: %s, instance: %s, host_id: %s",
        orgId, instanceId, hostId);
    return hostId;
  }

  /**
   * Create host tally buckets for a host
   *
   * @param hostId The host UUID (from hosts.id)
   * @param productTag The product tag (e.g., "rhel-for-x86-els-payg")
   * @param sla Service Level Agreement (e.g., "PREMIUM", "_ANY")
   * @param usage Usage type (e.g., "PRODUCTION", "_ANY")
   * @param billingProvider Billing provider (e.g., "aws", "azure", "red_hat", "_ANY")
   * @param billingAccountId Billing account ID
   * @param cores Number of cores
   * @param sockets Number of sockets
   * @param measurementType Hardware measurement type (e.g., "PHYSICAL", "VIRTUAL", "HYPERVISOR",
   *     "CLOUDS")
   */
  public void createHostTallyBucket(
      UUID hostId,
      String productTag,
      String sla,
      String usage,
      String billingProvider,
      String billingAccountId,
      Integer cores,
      Integer sockets,
      String measurementType) {
    Map<String, Object> params = new HashMap<>();
    params.put("hostId", hostId);
    params.put("productId", productTag);
    params.put("sla", sla);
    params.put("usage", usage);
    params.put("billingProvider", billingProvider);
    params.put("billingAccountId", billingAccountId);
    params.put("cores", cores);
    params.put("sockets", sockets);
    params.put("measurementType", measurementType);

    jdbcTemplate.update(
        "INSERT INTO host_tally_buckets (host_id, product_id, sla, usage, billing_provider, "
            + "billing_account_id, as_hypervisor, cores, sockets, measurement_type) "
            + "VALUES (:hostId, :productId, :sla, :usage, :billingProvider, :billingAccountId, "
            + "false, :cores, :sockets, :measurementType) "
            + "ON CONFLICT (host_id, product_id, sla, usage, billing_provider, billing_account_id, as_hypervisor) "
            + "DO UPDATE SET cores = EXCLUDED.cores, sockets = EXCLUDED.sockets, "
            + "measurement_type = EXCLUDED.measurement_type",
        params);

    Log.info(
        "Host tally bucket created for host_id: %s, product: %s, measurement_type: %s",
        hostId, productTag, measurementType);
  }

  /**
   * Create a complete mock host with tally buckets
   *
   * @param orgId Organization ID
   * @param instanceId Instance ID
   * @param productId Product tag
   * @param cores Number of cores
   * @param sockets Number of sockets
   * @return The UUID of the created host
   */
  public UUID createMockHostWithBuckets(
      String orgId, String instanceId, String productId, Integer cores, Integer sockets) {
    UUID hostId = createMockHost(orgId, instanceId);

    createHostTallyBucket(
        hostId,
        productId,
        "PREMIUM",
        "PRODUCTION",
        "aws",
        DEFAULT_BILLING_ACCOUNT,
        cores,
        sockets,
        "VIRTUAL");

    createHostTallyBucket(
        hostId,
        productId,
        "_ANY",
        "_ANY",
        "aws",
        DEFAULT_BILLING_ACCOUNT,
        cores,
        sockets,
        "VIRTUAL");

    Log.info(
        "Mock host with tally buckets created successfully for org: %s, instance: %s, host_id: %s, product_id: %s, cores: %d, sockets: %d",
        orgId, instanceId, hostId, productId, cores, sockets);

    return hostId;
  }

  public String generateRandomOrgId() {
    return String.format("%d", Math.abs(UUID.randomUUID().hashCode()) % 100000000);
  }

  public void waitForProcessing(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Interrupted while waiting", e);
    }
  }
}

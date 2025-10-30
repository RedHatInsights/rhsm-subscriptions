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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.redhat.swatch.component.tests.logging.Log;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

public class TallyDatabaseHelper {

  // Database configuration - loaded from test.yaml
  private static final String DB_HOST;
  private static final int DB_PORT;
  private static final String DB_NAME;
  private static final String DB_USER;
  private static final String DB_PASSWORD;

  private final NamedParameterJdbcTemplate jdbcTemplate;

  // Static initializer to load database properties from test.yaml
  static {
    try (InputStream input =
        TallyDatabaseHelper.class.getClassLoader().getResourceAsStream("test-config.yaml")) {
      if (input == null) {
        throw new RuntimeException("Unable to find test-config.yaml in classpath");
      }

      ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
      @SuppressWarnings("unchecked")
      Map<String, Object> config = mapper.readValue(input, Map.class);

      @SuppressWarnings("unchecked")
      Map<String, Object> dbConfig = (Map<String, Object>) config.get("db");

      if (dbConfig == null) {
        throw new RuntimeException("Missing 'db' configuration in test.yaml");
      }

      // Load database configuration
      DB_HOST = (String) dbConfig.getOrDefault("host", "localhost");
      DB_PORT = (Integer) dbConfig.getOrDefault("port", 5432);
      DB_NAME = (String) dbConfig.getOrDefault("name", "rhsm-subscriptions");
      DB_USER = (String) dbConfig.getOrDefault("username", "rhsm-subscriptions");
      DB_PASSWORD = (String) dbConfig.getOrDefault("password", "rhsm-subscriptions");

      Log.info(
          "Loaded database configuration from test.yaml - DB Host: %s, DB Port: %d",
          DB_HOST, DB_PORT);
    } catch (IOException e) {
      throw new RuntimeException("Failed to load test.yaml", e);
    }
  }

  /**
   * Default constructor that initializes the JDBC template with default database connection
   * settings.
   */
  public TallyDatabaseHelper() {
    this.jdbcTemplate = new NamedParameterJdbcTemplate(createDataSource());
  }

  /**
   * Constructor that accepts a pre-configured NamedParameterJdbcTemplate for dependency injection.
   *
   * @param jdbcTemplate The JDBC template to use for database operations
   */
  public TallyDatabaseHelper(NamedParameterJdbcTemplate jdbcTemplate) {
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

  /**
   * Create a mock host in the database for testing purposes.
   *
   * @param orgId Organization ID
   * @param instanceId Instance ID
   * @param billingAccountId Billing account ID
   * @return The UUID of the created host
   */
  public UUID createMockHost(String orgId, String instanceId, String billingAccountId) {
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
    hostParams.put("billingAccountId", billingAccountId);

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
   * @param billingAccountId Billing account ID
   * @param cores Number of cores
   * @param sockets Number of sockets
   * @return The UUID of the created host
   */
  public UUID createMockHostWithBuckets(
      String orgId,
      String instanceId,
      String productId,
      String billingAccountId,
      Integer cores,
      Integer sockets) {
    UUID hostId = createMockHost(orgId, instanceId, billingAccountId);

    createHostTallyBucket(
        hostId,
        productId,
        "PREMIUM",
        "PRODUCTION",
        "aws",
        billingAccountId,
        cores,
        sockets,
        "VIRTUAL");

    createHostTallyBucket(
        hostId, productId, "_ANY", "_ANY", "aws", billingAccountId, cores, sockets, "VIRTUAL");

    Log.info(
        "Mock host with tally buckets created successfully for org: %s, instance: %s, host_id: %s, product_id: %s, cores: %d, sockets: %d",
        orgId, instanceId, hostId, productId, cores, sockets);

    return hostId;
  }
}

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

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Temporary DB seeder for swatch-tally component tests.
 *
 * <p>This is intentionally isolated in CT utils so it can be removed later.
 *
 * <p>Connection is configured via env vars (with local docker-compose defaults):
 *
 * <ul>
 *   <li>SWATCH_CT_DB_HOST (default: localhost)
 *   <li>SWATCH_CT_DB_PORT (default: 5432)
 *   <li>SWATCH_CT_DB_NAME (default: rhsm-subscriptions)
 *   <li>SWATCH_CT_DB_USER (default: rhsm-subscriptions)
 *   <li>SWATCH_CT_DB_PASSWORD (default: rhsm-subscriptions)
 *   <li>SWATCH_CT_DB_SSLMODE (optional, e.g. disable/require)
 * </ul>
 */
public final class TallyDbHostSeeder {

  private static final String DEFAULT_DB_HOST = "localhost";
  private static final String DEFAULT_DB_PORT = "5432";
  private static final String DEFAULT_DB_NAME = "rhsm-subscriptions";
  private static final String DEFAULT_DB_USER = "rhsm-subscriptions";
  private static final String DEFAULT_DB_PASSWORD = "rhsm-subscriptions";

  private static final String HBI_INSTANCE_TYPE = "HBI_HOST";
  private static final String DEFAULT_BILLING_PROVIDER = "_ANY";
  private static final String DEFAULT_BILLING_ACCOUNT_ID = "_ANY";

  private TallyDbHostSeeder() {}

  /** Insert a single HBI_HOST in the swatch DB and return its UUID. */
  public static UUID insertHbiHost(String orgId, String inventoryId) {
    Objects.requireNonNull(orgId, "orgId is required");
    Objects.requireNonNull(inventoryId, "inventoryId is required");

    UUID hostId = UUID.randomUUID();
    String subManId = UUID.randomUUID().toString();
    OffsetDateTime now = OffsetDateTime.now();

    try (Connection conn = DriverManager.getConnection(jdbcUrl(), dbUser(), dbPassword())) {
      conn.setAutoCommit(false);

      try (PreparedStatement ps =
          conn.prepareStatement(
              "INSERT INTO account_services (org_id, service_type) VALUES (?, ?) ON CONFLICT DO NOTHING")) {
        ps.setString(1, orgId);
        ps.setString(2, HBI_INSTANCE_TYPE);
        ps.executeUpdate();
      }

      try (PreparedStatement ps =
          conn.prepareStatement(
              """
              INSERT INTO hosts
                (id, instance_id, inventory_id, insights_id, display_name, org_id,
                 subscription_manager_id, is_guest, is_unmapped_guest, is_hypervisor,
                 hardware_type, last_seen, instance_type, billing_provider, billing_account_id)
              VALUES
                (?, ?, ?, ?, ?, ?,
                 ?, ?, ?, ?,
                 ?, ?, ?, ?, ?)
              """)) {
        ps.setObject(1, hostId);
        ps.setString(2, inventoryId);
        ps.setString(3, inventoryId);
        ps.setString(4, inventoryId);
        ps.setString(5, inventoryId);
        ps.setString(6, orgId);
        ps.setString(7, subManId);
        ps.setBoolean(8, false);
        ps.setBoolean(9, false);
        ps.setBoolean(10, false);
        ps.setString(11, "PHYSICAL");
        ps.setObject(12, now);
        ps.setString(13, HBI_INSTANCE_TYPE);
        ps.setString(14, DEFAULT_BILLING_PROVIDER);
        ps.setString(15, DEFAULT_BILLING_ACCOUNT_ID);
        ps.executeUpdate();
      }

      conn.commit();
      return hostId;
    } catch (SQLException e) {
      throw new RuntimeException("Failed to seed host in DB: " + e.getMessage(), e);
    }
  }

  /**
   * Insert bucket rows for an existing host.
   *
   * <p>Inserts the 4 combinations of (sla=_ANY|sla) x (usage=_ANY|usage) to match existing local
   * seeding behavior.
   */
  public static void insertBuckets(
      UUID hostId,
      String productId,
      String sla,
      String usage,
      int cores,
      int sockets,
      String measurementType) {
    Objects.requireNonNull(hostId, "hostId is required");
    Objects.requireNonNull(productId, "productId is required");
    Objects.requireNonNull(sla, "sla is required");
    Objects.requireNonNull(usage, "usage is required");
    Objects.requireNonNull(measurementType, "measurementType is required");

    try (Connection conn = DriverManager.getConnection(jdbcUrl(), dbUser(), dbPassword())) {
      conn.setAutoCommit(false);
      String[] slas = new String[] {"_ANY", sla};
      String[] usages = new String[] {"_ANY", usage};
      for (String nextSla : slas) {
        for (String nextUsage : usages) {
          insertBucket(
              conn,
              hostId,
              productId,
              nextSla,
              nextUsage,
              DEFAULT_BILLING_PROVIDER,
              DEFAULT_BILLING_ACCOUNT_ID,
              cores,
              sockets,
              measurementType);
        }
      }
      conn.commit();
    } catch (SQLException e) {
      throw new RuntimeException("Failed to seed host buckets in DB: " + e.getMessage(), e);
    }
  }

  private static void insertBucket(
      Connection conn,
      UUID hostId,
      String productId,
      String sla,
      String usage,
      String billingProvider,
      String billingAccountId,
      int cores,
      int sockets,
      String measurementType)
      throws SQLException {
    try (PreparedStatement ps =
        conn.prepareStatement(
            """
            INSERT INTO host_tally_buckets
              (host_id, product_id, usage, sla, billing_provider, billing_account_id, as_hypervisor,
               cores, sockets, measurement_type, version)
            VALUES
              (?, ?, ?, ?, ?, ?, ?,
               ?, ?, ?, 0)
            """)) {
      ps.setObject(1, hostId);
      ps.setString(2, productId);
      ps.setString(3, usage);
      ps.setString(4, sla);
      ps.setString(5, billingProvider);
      ps.setString(6, billingAccountId);
      ps.setBoolean(7, false);
      ps.setInt(8, cores);
      ps.setInt(9, sockets);
      ps.setString(10, measurementType);
      ps.executeUpdate();
    }
  }

  private static String jdbcUrl() {
    String host = env("SWATCH_CT_DB_HOST", DEFAULT_DB_HOST);
    String port = env("SWATCH_CT_DB_PORT", DEFAULT_DB_PORT);
    String db = env("SWATCH_CT_DB_NAME", DEFAULT_DB_NAME);
    String sslmode = System.getenv("SWATCH_CT_DB_SSLMODE");

    String base = String.format("jdbc:postgresql://%s:%s/%s", host, port, db);
    if (sslmode == null || sslmode.isBlank()) {
      return base;
    }
    return base + "?sslmode=" + sslmode;
  }

  private static String dbUser() {
    return env("SWATCH_CT_DB_USER", DEFAULT_DB_USER);
  }

  private static String dbPassword() {
    return env("SWATCH_CT_DB_PASSWORD", DEFAULT_DB_PASSWORD);
  }

  private static String env(String name, String defaultValue) {
    String v = System.getenv(name);
    return v == null || v.isBlank() ? defaultValue : v;
  }

  // Intentionally no application logic here; this helper is temporary and removable.
}

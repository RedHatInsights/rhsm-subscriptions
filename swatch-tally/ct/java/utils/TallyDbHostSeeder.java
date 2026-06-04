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

import com.redhat.swatch.component.tests.api.db.DatabaseService;
import com.redhat.swatch.component.tests.api.db.TransactionContext;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;

/**
 * Temporary DB seeder for swatch-tally component tests.
 *
 * <p>This is intentionally isolated in CT utils so it can be removed later.
 *
 * <p>Database connection is provided by {@link DatabaseService} which is injected via constructor.
 */
public final class TallyDbHostSeeder {

  private static final String HBI_INSTANCE_TYPE = "HBI_HOST";

  private final DatabaseService database;

  public TallyDbHostSeeder(DatabaseService database) {
    this.database = database;
  }

  public record SeededHost(UUID hostId, String inventoryId, String subscriptionManagerId) {}

  /** Insert a single HBI_HOST in the swatch DB and return its UUID. */
  public UUID insertHbiHost(String orgId, String inventoryId) {
    return insertHost(orgId, inventoryId, "PHYSICAL", false, false, false, null, null).hostId();
  }

  /**
   * Insert a host with billing account and billing provider information and associated buckets.
   *
   * @param orgId the organization ID
   * @param inventoryId the inventory ID
   * @param productTag the product tag for buckets
   * @param billingProvider the billing provider (e.g., "AWS", "AZURE")
   * @param billingAccountId the billing account ID
   * @return the host UUID
   */
  public UUID insertHostWithBillingAccount(
      String orgId,
      String inventoryId,
      String productTag,
      String billingProvider,
      String billingAccountId) {
    return insertHostWithBillingAccountAndDate(
        orgId,
        inventoryId,
        productTag,
        billingProvider,
        billingAccountId,
        OffsetDateTime.now(ZoneOffset.UTC));
  }

  /**
   * Insert a host with billing account, billing provider information, custom last_seen date, and
   * associated buckets.
   *
   * @param orgId the organization ID
   * @param inventoryId the inventory ID
   * @param productTag the product tag for buckets
   * @param billingProvider the billing provider (e.g., "AWS", "AZURE")
   * @param billingAccountId the billing account ID
   * @param lastSeen the last_seen date for the host
   * @return the host UUID
   */
  public UUID insertHostWithBillingAccountAndDate(
      String orgId,
      String inventoryId,
      String productTag,
      String billingProvider,
      String billingAccountId,
      OffsetDateTime lastSeen) {
    Objects.requireNonNull(orgId, "orgId is required");
    Objects.requireNonNull(inventoryId, "inventoryId is required");
    Objects.requireNonNull(productTag, "productTag is required");
    Objects.requireNonNull(billingProvider, "billingProvider is required");
    Objects.requireNonNull(billingAccountId, "billingAccountId is required");
    Objects.requireNonNull(lastSeen, "lastSeen is required");

    UUID hostId = UUID.randomUUID();
    String subManId = UUID.randomUUID().toString();

    database.executeInTransaction(
        ctx -> {
          ensureAccountServiceRow(ctx, orgId);
          insertHostRow(
              ctx,
              hostId,
              inventoryId,
              subManId,
              orgId,
              false,
              false,
              false,
              "CLOUD",
              null,
              lastSeen,
              billingProvider,
              billingAccountId,
              null);
          insertBucket(
              ctx,
              hostId,
              productTag,
              "Premium",
              "Production",
              4,
              2,
              "CLOUD",
              billingProvider,
              billingAccountId);
        });

    return hostId;
  }

  /** Insert a host row only (no buckets) and return identifying fields for assertions. */
  public SeededHost insertHost(
      String orgId,
      String inventoryId,
      String hardwareType,
      boolean isGuest,
      boolean isUnmappedGuest,
      boolean isHypervisor,
      Integer numOfGuests,
      String hypervisorUuid) {
    Objects.requireNonNull(orgId, "orgId is required");
    Objects.requireNonNull(inventoryId, "inventoryId is required");

    UUID hostId = UUID.randomUUID();
    String subManId = UUID.randomUUID().toString();
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

    database.executeInTransaction(
        ctx -> {
          ensureAccountServiceRow(ctx, orgId);
          insertHostRow(
              ctx,
              hostId,
              inventoryId,
              subManId,
              orgId,
              isGuest,
              isUnmappedGuest,
              isHypervisor,
              hardwareType,
              numOfGuests,
              now,
              null,
              null,
              hypervisorUuid);
        });

    return new SeededHost(hostId, inventoryId, subManId);
  }

  /**
   * Insert bucket rows for an existing host.
   *
   * <p>Inserts the 4 combinations of (sla=_ANY|sla) x (usage=_ANY|usage) to match existing local
   * seeding behavior.
   */
  public void insertBuckets(
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

    database.executeInTransaction(
        ctx -> insertBucket(ctx, hostId, productId, sla, usage, cores, sockets, measurementType));
  }

  private void insertBucket(
      TransactionContext ctx,
      UUID hostId,
      String productId,
      String sla,
      String usage,
      int cores,
      int sockets,
      String measurementType)
      throws SQLException {
    insertBucket(ctx, hostId, productId, sla, usage, cores, sockets, measurementType, "");
  }

  private void insertBucket(
      TransactionContext ctx,
      UUID hostId,
      String productId,
      String sla,
      String usage,
      int cores,
      int sockets,
      String measurementType,
      String billingAccountId)
      throws SQLException {
    insertBucket(
        ctx, hostId, productId, sla, usage, cores, sockets, measurementType, "", billingAccountId);
  }

  private void insertBucket(
      TransactionContext ctx,
      UUID hostId,
      String productId,
      String sla,
      String usage,
      int cores,
      int sockets,
      String measurementType,
      String billingProvider,
      String billingAccountId)
      throws SQLException {
    ctx.executeUpdate(
        """
        INSERT INTO host_tally_buckets
          (host_id, product_id, usage, sla, as_hypervisor,
           cores, sockets, measurement_type, billing_provider, billing_account_id, version)
        VALUES
          (?, ?, ?, ?, ?,
           ?, ?, ?, ?, ?, 0)
        """,
        ps -> {
          ps.setObject(1, hostId);
          ps.setString(2, productId);
          ps.setString(3, usage);
          ps.setString(4, sla);
          ps.setBoolean(5, false);
          ps.setInt(6, cores);
          ps.setInt(7, sockets);
          ps.setString(8, measurementType);
          if (billingProvider == null || billingProvider.isEmpty()) {
            ps.setString(9, "");
          } else {
            ps.setString(9, billingProvider);
          }
          if (billingAccountId == null) {
            ps.setNull(10, Types.VARCHAR);
          } else {
            ps.setString(10, billingAccountId);
          }
        });
  }

  private void insertHostRow(
      TransactionContext ctx,
      UUID hostId,
      String inventoryId,
      String subManId,
      String orgId,
      boolean isGuest,
      boolean isUnmappedGuest,
      boolean isHypervisor,
      String hardwareType,
      Integer numOfGuests,
      OffsetDateTime lastSeen,
      String billingProvider,
      String billingAccountId,
      String hypervisorUuid)
      throws SQLException {
    ctx.executeUpdate(
        """
        INSERT INTO hosts
          (id, instance_id, inventory_id, insights_id, display_name, org_id,
           subscription_manager_id, is_guest, is_unmapped_guest, is_hypervisor,
           hardware_type, num_of_guests, last_seen, instance_type, billing_provider,
           billing_account_id, hypervisor_uuid)
        VALUES
          (?, ?, ?, ?, ?, ?,
           ?, ?, ?, ?,
           ?, ?, ?, ?, ?,
           ?, ?)
        """,
        ps -> {
          ps.setObject(1, hostId);
          ps.setString(2, inventoryId);
          ps.setString(3, inventoryId);
          ps.setString(4, inventoryId);
          ps.setString(5, inventoryId);
          ps.setString(6, orgId);
          ps.setString(7, subManId);
          ps.setBoolean(8, isGuest);
          ps.setBoolean(9, isUnmappedGuest);
          ps.setBoolean(10, isHypervisor);
          ps.setString(11, hardwareType);
          if (numOfGuests == null) {
            ps.setNull(12, Types.INTEGER);
          } else {
            ps.setInt(12, numOfGuests);
          }
          ps.setObject(13, lastSeen);
          ps.setString(14, HBI_INSTANCE_TYPE);
          if (billingProvider == null) {
            ps.setNull(15, Types.VARCHAR);
          } else {
            ps.setString(15, billingProvider);
          }
          if (billingAccountId == null) {
            ps.setNull(16, Types.VARCHAR);
          } else {
            ps.setString(16, billingAccountId);
          }
          ps.setString(17, hypervisorUuid);
        });
  }

  private void ensureAccountServiceRow(TransactionContext ctx, String orgId) throws SQLException {
    ctx.executeUpdate(
        "INSERT INTO account_services (org_id, service_type) VALUES (?, ?) ON CONFLICT DO NOTHING",
        ps -> {
          ps.setString(1, orgId);
          ps.setString(2, HBI_INSTANCE_TYPE);
        });
  }

  // Intentionally no application logic here; this helper is temporary and removable.
}

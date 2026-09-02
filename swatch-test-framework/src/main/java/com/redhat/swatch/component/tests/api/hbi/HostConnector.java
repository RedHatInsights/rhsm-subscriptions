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
package com.redhat.swatch.component.tests.api.hbi;

import java.util.List;
import java.util.UUID;

/**
 * Interface for seeding hosts into HBI via different mechanisms.
 *
 * <p>Implementations include:
 *
 * <ul>
 *   <li>{@link HbiDbConnector} - Direct database insertion (fast, for component tests)
 *   <li>KafkaConnector - Kafka message (realistic, tests full ingestion pipeline)
 * </ul>
 */
public interface HostConnector {

  /**
   * Seed a single host.
   *
   * @param host the host data to seed
   * @return information about the seeded host
   */
  SeededHost seed(Host host);

  /**
   * Update a previously-seeded host in place, replacing its facts and system-profile columns with
   * whatever is currently set on {@code host}. Fact groups not set on {@code host} are cleared -
   * callers that want to preserve a previously-set fact group (e.g. Satellite facts while only
   * updating RHSM facts) must reuse the same {@link Host} instance that was originally seeded.
   *
   * @param host the full current host data to write; {@link Host#getId()} identifies the row
   * @return information about the updated host
   */
  SeededHost update(Host host);

  /**
   * Seed multiple hosts in batch.
   *
   * <p>Implementations may optimize batch operations (e.g., bulk INSERT, batch Kafka send).
   *
   * @param hosts the list of hosts to seed
   * @return list of seeded host information
   */
  List<SeededHost> seedBatch(List<Host> hosts);

  /**
   * Delete a host by ID.
   *
   * @param hostId the host UUID to delete
   */
  void cleanup(UUID hostId);

  /**
   * Check if a host exists.
   *
   * @param hostId the host UUID to check
   * @return true if the host exists, false otherwise
   */
  boolean hostExists(UUID hostId);

  /**
   * Record of a seeded host for test assertions.
   *
   * @param hostId the UUID assigned to the host in HBI
   * @param inventoryId the inventory ID
   * @param subscriptionManagerId the subscription manager ID
   * @param orgId the organization ID
   */
  record SeededHost(UUID hostId, String inventoryId, String subscriptionManagerId, String orgId) {}
}

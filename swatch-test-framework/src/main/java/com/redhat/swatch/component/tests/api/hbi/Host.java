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

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;

/**
 * Host data container with fluent setters.
 *
 * <p>Represents host data that will be seeded into the HBI database. There is a single kind of HBI
 * host; what varies is which reporters contributed facts to it. Reporter facts are modeled by
 * {@link RhsmFacts}, {@link SatelliteFacts}, and {@link QpcFacts}; system-profile fields by {@link
 * SystemProfileFacts}.
 *
 * <p>Usage:
 *
 * <pre>
 * Host baseHost = new Host(orgId)
 *     .rhsmFacts(RhsmFacts.builder().defaultFacts().build())
 *     .systemProfileFacts(SystemProfileFacts.builder().numberOfSockets(2).build());
 * </pre>
 */
@Getter
public class Host {

  // ===== Core Identity =====
  private String orgId;
  private UUID id; // hbi.hosts.id, known only after insert()
  private String subscriptionManagerId;
  private String insightsId;
  private String displayName;
  private String providerId; // Cloud provider instance ID
  private String account; // Deprecated, but included for completeness

  // ===== System Profile =====
  private SystemProfileFacts systemProfileFacts;

  // ===== Reporter Facts (stored as JSONB in HBI) =====
  private RhsmFacts rhsmFacts;
  private QpcFacts qpcFacts;
  private SatelliteFacts satelliteFacts;
  private YupanaFacts yupanaFacts;

  // ===== Reporter Info =====
  private String reporter = "component-test";
  private String[] reporters = new String[] {"component-test"};

  // ===== Timestamps =====
  private OffsetDateTime createdOn;
  private OffsetDateTime modifiedOn;
  private OffsetDateTime lastCheckIn;

  // ===== Additional Fields =====
  private String conversionsActivity;
  private String billingModel;

  /**
   * Create a new Host with the specified organization ID.
   *
   * @param orgId the organization ID (required)
   */
  public Host(String orgId) {
    this.orgId = Objects.requireNonNull(orgId, "orgId is required");
  }

  // ===== Core Identity Setters =====

  public Host subscriptionManagerId(String subscriptionManagerId) {
    this.subscriptionManagerId = subscriptionManagerId;
    return this;
  }

  public Host insightsId(String insightsId) {
    this.insightsId = insightsId;
    return this;
  }

  public Host displayName(String displayName) {
    this.displayName = displayName;
    return this;
  }

  /**
   * Remember the {@code hbi.hosts.id} row id for this host, so a {@link HostBuilder} can later
   * {@code update()} the same row it {@code insert()}-ed.
   */
  Host id(UUID id) {
    this.id = id;
    return this;
  }

  public Host orgId(String orgId) {
    this.orgId = orgId;
    return this;
  }

  public Host providerId(String providerId) {
    this.providerId = providerId;
    return this;
  }

  public Host account(String account) {
    this.account = account;
    return this;
  }

  // ===== System Profile Setter =====

  public Host systemProfileFacts(SystemProfileFacts systemProfileFacts) {
    this.systemProfileFacts = systemProfileFacts;
    return this;
  }

  // ===== Reporter Facts Setters =====

  public Host rhsmFacts(RhsmFacts rhsmFacts) {
    this.rhsmFacts = rhsmFacts;
    return this;
  }

  public Host qpcFacts(QpcFacts qpcFacts) {
    this.qpcFacts = qpcFacts;
    return this;
  }

  public Host satelliteFacts(SatelliteFacts satelliteFacts) {
    this.satelliteFacts = satelliteFacts;
    return this;
  }

  public Host yupanaFacts(YupanaFacts yupanaFacts) {
    this.yupanaFacts = yupanaFacts;
    return this;
  }

  // ===== Reporter Setters =====

  public Host reporter(String reporter) {
    this.reporter = reporter;
    return this;
  }

  public Host reporters(String... reporters) {
    this.reporters = reporters;
    return this;
  }

  // ===== Timestamp Setters =====

  public Host createdOn(OffsetDateTime createdOn) {
    this.createdOn = createdOn;
    return this;
  }

  public Host modifiedOn(OffsetDateTime modifiedOn) {
    this.modifiedOn = modifiedOn;
    return this;
  }

  public Host lastCheckIn(OffsetDateTime lastCheckIn) {
    this.lastCheckIn = lastCheckIn;
    return this;
  }

  // ===== Additional Field Setters =====

  public Host conversionsActivity(String conversionsActivity) {
    this.conversionsActivity = conversionsActivity;
    return this;
  }

  public Host billingModel(String billingModel) {
    this.billingModel = billingModel;
    return this;
  }
}

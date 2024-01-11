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
package org.candlepin.subscriptions.db.model;

import com.redhat.swatch.configuration.registry.MetricId;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** Model object to represent pieces of tally data. */
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
@Entity
@Table(name = "tally_snapshots")
public class TallySnapshot implements Serializable {

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  private UUID id;

  @Column(name = "snapshot_date")
  private OffsetDateTime snapshotDate;

  @Column(name = "product_id")
  private String productId;

  @Column(name = "org_id")
  private String orgId;

  @Builder.Default
  @Column(name = "sla")
  private ServiceLevel serviceLevel = ServiceLevel._ANY;

  @Builder.Default
  @Column(name = "usage")
  private Usage usage = Usage._ANY;

  @Builder.Default
  @Column(name = "billing_provider")
  private BillingProvider billingProvider = BillingProvider._ANY;

  @Column(name = "billing_account_id")
  private String billingAccountId;

  @Enumerated(EnumType.STRING)
  @Column(name = "granularity")
  private Granularity granularity;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "tally_measurements", joinColumns = @JoinColumn(name = "snapshot_id"))
  @Column(name = "value")
  @MapKeyClass(TallyMeasurementKey.class)
  @Builder.Default
  private Map<TallyMeasurementKey, Double> tallyMeasurements = new HashMap<>();

  public int getMeasurementAsInteger(HardwareMeasurementType type, MetricId uom) {
    return Optional.ofNullable(getMeasurement(type, uom)).map(Double::intValue).orElse(0);
  }

  public Double getMeasurement(HardwareMeasurementType type, MetricId metricId) {
    TallyMeasurementKey key = new TallyMeasurementKey(type, metricId.getValue());
    return getTallyMeasurements().get(key);
  }

  public void setMeasurement(HardwareMeasurementType type, MetricId metricId, Double value) {
    TallyMeasurementKey key = new TallyMeasurementKey(type, metricId.getValue());
    tallyMeasurements.put(key, value);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof TallySnapshot)) {
      return false;
    }
    TallySnapshot that = (TallySnapshot) o;
    return Objects.equals(snapshotDate, that.snapshotDate)
        && Objects.equals(productId, that.productId)
        && Objects.equals(orgId, that.orgId)
        && serviceLevel == that.serviceLevel
        && usage == that.usage
        && granularity == that.granularity
        && billingProvider == that.billingProvider
        && Objects.equals(billingAccountId, that.billingAccountId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        snapshotDate,
        productId,
        orgId,
        serviceLevel,
        usage,
        granularity,
        billingProvider,
        billingAccountId);
  }
}

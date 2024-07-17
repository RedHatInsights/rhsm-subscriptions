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
package com.redhat.swatch.contract.repository;

import com.redhat.swatch.contract.repository.SubscriptionEntity.SubscriptionCompoundId;
import com.redhat.swatch.contract.repository.SubscriptionMeasurementEntity.SubscriptionMeasurementKey;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** Capacity provided by a subscription for a given product. */
@Entity
@Getter
@Setter
@ToString
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor
@Table(name = "subscription_measurements")
@IdClass(SubscriptionMeasurementKey.class)
public class SubscriptionMeasurementEntity implements Serializable {
  @Id
  @ManyToOne
  @JoinColumn(name = "subscription_id", referencedColumnName = "subscription_id")
  @JoinColumn(name = "start_date", referencedColumnName = "start_date")
  private SubscriptionEntity subscription;

  @Id
  @Column(name = "metric_id")
  private String metricId;

  @Id
  @Column(name = "measurement_type")
  private String measurementType;

  @Column(name = "value")
  private Double value;

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof SubscriptionMeasurementEntity measurement)) {
      return false;
    }

    return Objects.equals(metricId, measurement.getMetricId())
        && Objects.equals(measurementType, measurement.getMeasurementType())
        && Objects.equals(value, measurement.getValue())
        && Objects.equals(
            subscription.getSubscriptionId(), measurement.getSubscription().getSubscriptionId())
        && Objects.equals(
            subscription.getStartDate(), measurement.getSubscription().getStartDate());
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        metricId, measurementType, subscription.getSubscriptionId(), subscription.getStartDate());
  }

  /** Primary key for a subscription_measurement record */
  @Getter
  @Setter
  public static class SubscriptionMeasurementKey implements Serializable {
    // NB: this name must match the field name used in the dependent entity
    // (SubscriptionMeasurementEntity) See JPA 2.1 specification, section 2.4.1.3 example 2a
    private SubscriptionCompoundId subscription;

    private String metricId;

    private String measurementType;

    public SubscriptionMeasurementKey() {}

    public SubscriptionMeasurementKey(
        SubscriptionCompoundId subscription, String metricId, String measurementType) {
      this.subscription = subscription;
      this.metricId = metricId;
      this.measurementType = measurementType;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof SubscriptionMeasurementKey measurementKey)) {
        return false;
      }

      return Objects.equals(metricId, measurementKey.getMetricId())
          && Objects.equals(measurementType, measurementKey.getMeasurementType())
          && Objects.equals(
              subscription.getSubscriptionId(),
              measurementKey.getSubscription().getSubscriptionId())
          && Objects.equals(
              subscription.getStartDate(), measurementKey.getSubscription().getStartDate());
    }

    @Override
    public int hashCode() {
      return Objects.hash(
          metricId, measurementType, subscription.getSubscriptionId(), subscription.getStartDate());
    }
  }
}

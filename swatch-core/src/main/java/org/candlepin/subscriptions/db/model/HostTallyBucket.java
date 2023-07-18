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

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.io.Serializable;
import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** Represents a bucket that this host contributes to. */
@ToString
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "host_tally_buckets")
public class HostTallyBucket implements Serializable {

  @EmbeddedId private HostBucketKey key;

  private int cores;
  private int sockets;

  @Enumerated(EnumType.STRING)
  @Column(name = "measurement_type")
  private HardwareMeasurementType measurementType;

  @ToString.Exclude
  @MapsId("hostId")
  @ManyToOne(fetch = FetchType.LAZY)
  private Host host;

  // Version to enable optimistic locking
  @Version @Column private Integer version;

  @SuppressWarnings("java:S107")
  public HostTallyBucket(
      Host host,
      String productId,
      ServiceLevel sla,
      Usage usage,
      BillingProvider billingProvider,
      String billingAccountId,
      Boolean asHypervisor,
      int cores,
      int sockets,
      HardwareMeasurementType type) {
    this.host = host;
    setKey(
        new HostBucketKey(
            host, productId, sla, usage, billingProvider, billingAccountId, asHypervisor));
    this.cores = cores;
    this.sockets = sockets;
    this.measurementType = type;
  }

  public void setHost(Host host) {
    this.host = host;
    this.key.setHostId(host == null ? null : host.getId());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (!(o instanceof HostTallyBucket)) {
      return false;
    }

    HostTallyBucket bucket = (HostTallyBucket) o;
    return cores == bucket.cores
        && sockets == bucket.sockets
        && Objects.equals(key, bucket.key)
        && measurementType == bucket.measurementType;
  }

  @Override
  public int hashCode() {
    return Objects.hash(key, cores, sockets, measurementType);
  }
}

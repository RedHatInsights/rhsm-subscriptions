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

import static java.util.Optional.ofNullable;

import com.redhat.swatch.configuration.registry.MetricId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Immutable;

@Setter
@Getter
@Entity
@Immutable
@Table(name = "tally_instance_payg_view")
public class TallyInstancePaygView extends TallyInstanceView {

  @Column(name = "month")
  private String month;

  @Override
  public double getMetricValue(MetricId metricId) {
    if (getMetrics().containsKey(metricId)) {
      return ofNullable(getMetrics().get(metricId)).orElse(0.0);
    }

    return 0;
  }
}

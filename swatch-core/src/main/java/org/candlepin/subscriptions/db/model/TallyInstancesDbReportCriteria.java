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
import com.redhat.swatch.configuration.registry.ProductId;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import org.candlepin.subscriptions.utilization.api.v1.model.SortDirection;

/** Common criteria that can be used to filter instances, subscriptions, and tally snapshots */
@Data
@Builder
public class TallyInstancesDbReportCriteria {
  private String orgId;
  private ProductId productId;
  private ServiceLevel sla;
  private Usage usage;
  private String displayNameSubstring;
  private Integer minCores;
  private Integer minSockets;
  private String month;
  private MetricId metricId;
  private BillingProvider billingProvider;
  private String billingAccountId;
  private List<HardwareMeasurementType> hardwareMeasurementTypes;
  private String sort;
  private SortDirection sortDirection;
  private OffsetDateTime beginning;
  private OffsetDateTime ending;
}

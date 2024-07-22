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
package org.candlepin.subscriptions.util;

import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.HardwareMeasurementType;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.utilization.api.v2.model.BillingProviderType;
import org.candlepin.subscriptions.utilization.api.v2.model.PageLinks;
import org.candlepin.subscriptions.utilization.api.v2.model.ReportCategory;
import org.candlepin.subscriptions.utilization.api.v2.model.ServiceLevelType;
import org.candlepin.subscriptions.utilization.api.v2.model.UsageType;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ApiModelMapperV2 {
  org.candlepin.subscriptions.db.model.ReportCategory map(ReportCategory reportCategory);

  ServiceLevel map(ServiceLevelType serviceLevelType);

  Usage map(UsageType usage);

  ServiceLevelType map(ServiceLevel sanitizedServiceLevel);

  UsageType map(Usage sanitizedUsage);

  BillingProvider map(BillingProviderType billingProviderType);

  BillingProviderType map(BillingProvider billingProvider);

  PageLinks map(org.candlepin.subscriptions.resteasy.PageLinks pageLinks);

  @SuppressWarnings("Duplicates")
  default ReportCategory measurementTypeToReportCategory(HardwareMeasurementType measurementType) {
    if (HardwareMeasurementType.isSupportedCloudProvider(measurementType.name())) {
      return ReportCategory.CLOUD;
    }
    return switch (measurementType) {
      case VIRTUAL -> ReportCategory.VIRTUAL;
      case PHYSICAL -> ReportCategory.PHYSICAL;
      case HYPERVISOR -> ReportCategory.HYPERVISOR;
      default -> null;
    };
  }
}

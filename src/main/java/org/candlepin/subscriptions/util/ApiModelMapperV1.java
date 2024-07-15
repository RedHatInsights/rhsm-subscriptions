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

import com.redhat.swatch.configuration.util.MetricIdUtils;
import java.util.Optional;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.Granularity;
import org.candlepin.subscriptions.db.model.HardwareMeasurementType;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.utilization.api.v1.model.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ApiModelMapperV1 {
  org.candlepin.subscriptions.db.model.ReportCategory map(ReportCategory reportCategory);

  org.candlepin.subscriptions.db.model.BillingCategory map(BillingCategory billingCategory);

  GranularityType map(Granularity granularity);

  ServiceLevel map(ServiceLevelType serviceLevelType);

  Usage map(UsageType usage);

  ServiceLevelType map(ServiceLevel sanitizedServiceLevel);

  UsageType map(Usage sanitizedUsage);

  BillingProvider map(BillingProviderType billingProviderType);

  BillingProviderType map(BillingProvider billingProvider);

  PageLinks map(org.candlepin.subscriptions.resteasy.PageLinks pageLinks);

  @Mapping(target = "cores", expression = "java(getCores(host))")
  @Mapping(target = "sockets", expression = "java(getSockets(host))")
  @Mapping(target = "numberOfGuests", source = "numOfGuests")
  @Mapping(target = "isUnmappedGuest", source = "unmappedGuest")
  @Mapping(target = "isHypervisor", source = "hypervisor")
  @Mapping(target = "coreHours", ignore = true)
  @Mapping(target = "instanceHours", ignore = true)
  @Mapping(target = "measurementType", ignore = true)
  Host map(org.candlepin.subscriptions.db.model.Host host);

  default Integer getCores(org.candlepin.subscriptions.db.model.Host host) {
    return Optional.ofNullable(host.getMeasurement(MetricIdUtils.getCores().getValue()))
        .map(Double::intValue)
        .orElse(null);
  }

  default Integer getSockets(org.candlepin.subscriptions.db.model.Host host) {
    return Optional.ofNullable(host.getMeasurement(MetricIdUtils.getSockets().getValue()))
        .map(Double::intValue)
        .orElse(null);
  }

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

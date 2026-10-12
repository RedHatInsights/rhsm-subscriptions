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
package com.redhat.swatch.hbi.events.normalization.facts;

import com.redhat.swatch.hbi.events.dtos.hbi.HbiHost;
import com.redhat.swatch.hbi.events.dtos.hbi.HbiHostSystemProfile;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SystemProfileFacts {

  public static final String HOST_TYPE_FACT = "host_type";
  public static final String HYPERVISOR_UUID_FACT = "virtual_host_uuid";
  public static final String INFRASTRUCTURE_TYPE_FACT = "infrastructure_type";
  public static final String CORES_PER_SOCKET_FACT = "cores_per_socket";
  public static final String SOCKETS_FACT = "number_of_sockets";
  public static final String CPUS_FACT = "number_of_cpus";
  public static final String THREADS_PER_CORE_FACT = "threads_per_core";
  public static final String CLOUD_PROVIDER_FACT = "cloud_provider";
  public static final String ARCH_FACT = "arch";
  public static final String IS_MARKETPLACE_FACT = "is_marketplace";
  public static final String INSTALLED_PRODUCTS_FACT = "installed_products";
  public static final String INSTALLED_PRODUCT_ID_FACT = "id";
  public static final String CONVERSIONS_FACT = "conversions";
  public static final String CONVERSIONS_ACTIVITY = "activity";

  private final String hostType;
  private final String hypervisorUuid;
  private final String infrastructureType;
  private final Integer coresPerSocket;
  private final Integer sockets;
  private final Integer cpus;
  private final Integer threadsPerCore;
  private final String cloudProvider;
  private final String arch;
  private final Boolean isMarketplace;
  private final Boolean is3rdPartyMigrated;
  private final Set<String> productIds;

  public SystemProfileFacts(HbiHost host) {
    if (Objects.isNull(host)) {
      throw new IllegalArgumentException(
          "HbiHost cannot be null when initializing system profile facts");
    }

    HbiHostSystemProfile systemProfile =
        Objects.requireNonNullElse(host.getSystemProfile(), new HbiHostSystemProfile());

    hostType = systemProfile.getHostType();
    hypervisorUuid = systemProfile.getHypervisorUuid();
    infrastructureType = systemProfile.getInfrastructureType();
    coresPerSocket = systemProfile.getCoresPerSocket();
    sockets = systemProfile.getSockets();
    cpus = systemProfile.getCpus();
    threadsPerCore = systemProfile.getThreadsPerCore();
    cloudProvider = systemProfile.getCloudProvider();
    arch = systemProfile.getArch();
    isMarketplace = systemProfile.getIsMarketplace();
    productIds = getInstalledProductIds(systemProfile);
    is3rdPartyMigrated = getConversionActivity(systemProfile);
  }

  private Set<String> getInstalledProductIds(HbiHostSystemProfile systemProfile) {
    List<HbiHostSystemProfile.InstalledProduct> installedProducts =
        systemProfile.getInstalledProducts();
    if (Objects.isNull(installedProducts)) {
      return Collections.emptySet();
    }
    return installedProducts.stream()
        .map(HbiHostSystemProfile.InstalledProduct::getId)
        .filter(Objects::nonNull)
        .filter(id -> !id.isEmpty())
        .collect(Collectors.toSet());
  }

  private Boolean getConversionActivity(HbiHostSystemProfile systemProfile) {
    HbiHostSystemProfile.Conversion conversion = systemProfile.getConversions();
    if (Objects.isNull(conversion) || Objects.isNull(conversion.getActivity())) {
      return Boolean.FALSE;
    }
    return conversion.getActivity();
  }
}

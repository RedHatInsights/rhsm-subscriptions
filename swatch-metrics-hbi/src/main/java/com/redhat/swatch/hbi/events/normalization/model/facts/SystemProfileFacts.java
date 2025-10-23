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
package com.redhat.swatch.hbi.events.normalization.model.facts;

import com.redhat.swatch.hbi.events.dtos.hbi.HbiHost;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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

  @SuppressWarnings("unchecked")
  public SystemProfileFacts(HbiHost host) {
    if (Objects.isNull(host)) {
      throw new IllegalArgumentException(
          "HbiHost cannot be null when initializing system profile facts");
    }

    Map<String, Object> systemProfile =
        Optional.ofNullable(host.getSystemProfile()).orElse(new HashMap<>());
    hostType = (String) systemProfile.get(HOST_TYPE_FACT);
    hypervisorUuid = (String) systemProfile.get(HYPERVISOR_UUID_FACT);
    infrastructureType = (String) systemProfile.get(INFRASTRUCTURE_TYPE_FACT);
    coresPerSocket = (Integer) systemProfile.get(CORES_PER_SOCKET_FACT);
    sockets = (Integer) systemProfile.get(SOCKETS_FACT);
    cpus = (Integer) systemProfile.get(CPUS_FACT);
    threadsPerCore = (Integer) systemProfile.get(THREADS_PER_CORE_FACT);
    cloudProvider = (String) systemProfile.get(CLOUD_PROVIDER_FACT);
    arch = (String) systemProfile.get(ARCH_FACT);
    isMarketplace = (Boolean) systemProfile.getOrDefault(IS_MARKETPLACE_FACT, Boolean.FALSE);
    productIds = getInstalledProductIds(systemProfile);

    Map<String, Object> conversions =
        (Map<String, Object>) systemProfile.getOrDefault(CONVERSIONS_FACT, new HashMap<>());
    is3rdPartyMigrated = (Boolean) conversions.getOrDefault(CONVERSIONS_ACTIVITY, Boolean.FALSE);
  }

  @SuppressWarnings("unchecked")
  private Set<String> getInstalledProductIds(Map<String, Object> systemProfileRawFacts) {
    List<Map<String, String>> installedProductMap =
        (List<Map<String, String>>)
            systemProfileRawFacts.getOrDefault(INSTALLED_PRODUCTS_FACT, new ArrayList<>());
    return installedProductMap.stream()
        .map(ip -> ip.getOrDefault(INSTALLED_PRODUCT_ID_FACT, ""))
        .filter(id -> !id.isEmpty())
        .collect(Collectors.toSet());
  }
}

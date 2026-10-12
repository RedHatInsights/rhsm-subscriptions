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
package com.redhat.swatch.hbi.events.dtos.hbi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(Include.NON_NULL)
public class HbiHostSystemProfile {

  @JsonProperty("host_type")
  private String hostType;

  @JsonProperty("virtual_host_uuid")
  private String hypervisorUuid;

  @JsonProperty("infrastructure_type")
  private String infrastructureType;

  @JsonProperty("cores_per_socket")
  private Integer coresPerSocket;

  @JsonProperty("number_of_sockets")
  private Integer sockets;

  @JsonProperty("number_of_cpus")
  private Integer cpus;

  @JsonProperty("threads_per_core")
  private Integer threadsPerCore;

  @JsonProperty("cloud_provider")
  private String cloudProvider;

  private String arch;

  @JsonProperty("is_marketplace")
  private Boolean isMarketplace = Boolean.FALSE;

  @JsonProperty("installed_products")
  private List<InstalledProduct> installedProducts;

  @JsonProperty("conversions")
  private Conversion conversions;

  @JsonIgnoreProperties(ignoreUnknown = true)
  @Data
  public static class InstalledProduct {
    private String id;
  }

  @Data
  public static class Conversion {
    private Boolean activity;
  }
}

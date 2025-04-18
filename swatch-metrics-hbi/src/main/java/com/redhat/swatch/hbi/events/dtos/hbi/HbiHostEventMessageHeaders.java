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

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class HbiHostEventMessageHeaders {
  @JsonProperty("event_type")
  public String eventType;

  @JsonProperty("request_id")
  public String requestId;

  public String producer;

  @JsonProperty("insights_id")
  public String insightsId;

  public String reporter;

  @JsonProperty("host_type")
  public String hostType;

  @JsonProperty("os_name")
  public String osName;

  @JsonProperty("is_boot_c")
  public String isBootc = "False";
}

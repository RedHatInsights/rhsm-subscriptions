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
package com.redhat.swatch.hbi.events.configuration;

import com.fasterxml.jackson.core.JsonGenerator.Feature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.redhat.swatch.hbi.events.normalization.MeasurementNormalizer;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.candlepin.clock.ApplicationClock;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Dependent
@Getter
@Setter
public class ApplicationConfiguration {

  @Produces
  public ApplicationClock applicationClock() {
    return new ApplicationClock();
  }

  @Produces
  public ObjectMapper objectMapper() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
    mapper.configure(Feature.IGNORE_UNKNOWN, true);
    return mapper;
  }

  /**
   * The duration after the inventory's stale_timestamp that the record will be culled. Currently,
   * HBI is calculating this value and setting it on messages. Right now the default is:
   * stale_timestamp + 14 days. Adding this as a configuration setting since we may need to adjust
   * it at some point to match.
   */
  @ConfigProperty(name = "swatch-metrics-hbi.culling-offset")
  Duration cullingOffset;

  @ConfigProperty(name = "swatch-metrics-hbi.host-last-sync-threshold")
  Duration hostLastSyncThreshold;

  /**
   * This property enables that all the products use the new formula to calculate the number of
   * virtual CPUs (vCPUs). If disabled, the new formula will only be used for the OpenShift
   * Container products. See more in {@link MeasurementNormalizer}.
   *
   * <p>The formula to calculate the number of virtual CPUs (vCPUs) is based on the number of
   * threads per core which previously was hard-coded to 2.0. After <a
   * href="https://issues.redhat.com/browse/SWATCH-80">SWATCH-80</a>, the number of threads per core
   * is given from a new system profile fact. If absent, then we can also calculate it from another
   * new system profile fact which is the number of CPUs. However, we are not sure if the new system
   * profile facts are only valid for the OpenShift Container products. Therefore, we see an extreme
   * risk of applying the new system facts to all products, so if we detect this is not the desired
   * behaviour, we can disable this property to only apply the new system profile facts to the
   * OpenShift Container products.
   */
  @ConfigProperty(name = "swatch-metrics-hbi.use-cpu-system-facts-for-all-products")
  boolean useCpuSystemFactsForAllProducts;
}

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
package org.candlepin.subscriptions.tally.facts;

import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.candlepin.subscriptions.db.model.HardwareMeasurementType;
import org.candlepin.subscriptions.db.model.HostHardwareType;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Usage;

/** A normalized version of an inventory host's facts. */
@Getter
@Setter
public class NormalizedFacts {

  private Set<String> products;
  private ServiceLevel sla = ServiceLevel.EMPTY;
  private Usage usage = Usage.EMPTY;
  private Integer cores;
  private Integer sockets;
  private String orgId;

  /** Subscription-manager ID (UUID) of the hypervisor for this system */
  private String hypervisorUuid;

  private boolean isHypervisor;
  private boolean isHypervisorUnknown;
  private boolean isMarketplace;
  private boolean is3rdPartyConversion;
  private HostHardwareType hardwareType;
  private HardwareMeasurementType cloudProviderType;

  public NormalizedFacts() {
    products = new HashSet<>();
  }

  /**
   * Get the Subscription-manager ID (UUID) of the hypervisor for this system, if it's a guest and
   * its hypervisor is known.
   *
   * @return hypervisor UUID if known; otherwise, null
   */
  public String getHypervisorUuid() {
    return hypervisorUuid;
  }

  /**
   * Set the Subscription-manager ID (UUID) of the hypervisor for this system, if it's a guest and
   * its hypervisor is known.
   */
  public void setHypervisorUuid(String hypervisorUuid) {
    this.hypervisorUuid = hypervisorUuid;
  }

  public boolean isVirtual() {
    return getHardwareType() == HostHardwareType.VIRTUALIZED;
  }
}

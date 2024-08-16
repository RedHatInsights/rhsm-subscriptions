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
package org.candlepin.subscriptions.event;

import java.util.Objects;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.json.Event.BillingProvider;
import org.candlepin.subscriptions.json.Event.HardwareType;
import org.candlepin.subscriptions.json.Event.Sla;
import org.candlepin.subscriptions.json.Event.Usage;

/**
 * This class defines the attributes that trigger an event amendment when overlapping events are
 * encountered. The attribute set is determined based on the UsageCalculation.key and account
 * calculation's usage requirements.
 */
@EqualsAndHashCode
@Getter
public class UsageDescriptor {

  private final HardwareType hardwareType;
  private final Sla sla;
  private final Usage usage;
  private final BillingProvider billingProvider;
  private final String billingAccountId;

  public UsageDescriptor(Event event) {
    this.hardwareType = event.getHardwareType();
    this.sla = event.getSla();
    this.usage = event.getUsage();
    this.billingProvider = event.getBillingProvider();
    this.billingAccountId =
        Objects.isNull(event.getBillingAccountId())
            ? null
            : event.getBillingAccountId().orElse(null);
  }
}

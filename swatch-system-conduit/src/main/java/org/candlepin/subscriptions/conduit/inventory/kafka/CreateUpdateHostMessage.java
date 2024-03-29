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
package org.candlepin.subscriptions.conduit.inventory.kafka;

import java.util.HashMap;
import java.util.Map;
import org.candlepin.subscriptions.conduit.json.inventory.HbiHost;

/**
 * Represents a message that is sent to the inventory service's kafka instance to request the
 * creation/update of a host in the inventory service.
 */
public class CreateUpdateHostMessage extends HostOperationMessage<Map<String, String>, HbiHost> {

  public CreateUpdateHostMessage() {
    this.operation = "add_host";
    this.metadata = new HashMap<>();
  }

  public CreateUpdateHostMessage(HbiHost host) {
    super("add_host", new HashMap<>(), host);
  }

  public void setMetadata(String key, String value) {
    this.metadata.put(key, value);
  }
}

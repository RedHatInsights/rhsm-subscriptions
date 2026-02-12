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
package api;

import com.redhat.swatch.component.tests.api.DefaultMessageValidator;
import org.candlepin.subscriptions.conduit.inventory.kafka.CreateUpdateHostMessage;

public class MessageValidators {

  /**
   * Creates a validator that matches CreateUpdateHostMessage messages for a specific orgId.
   *
   * @param orgId the orgId to match
   * @return a MessageValidator that matches add_host message of the given orgId
   */
  public static DefaultMessageValidator<CreateUpdateHostMessage> addHostMessageMatchesOrgId(
      String orgId) {
    var operation = "add_host";

    return new DefaultMessageValidator<>(
        message -> {
          if (message == null || message.getData() == null) {
            return false;
          }
          return operation.equals(message.getOperation())
              && orgId.equals(message.getData().getOrgId());
        },
        CreateUpdateHostMessage.class);
  }
}

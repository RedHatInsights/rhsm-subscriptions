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
package org.candlepin.subscriptions.tally.admin;

import org.candlepin.subscriptions.tally.MarketplaceResendTallyController;
import org.candlepin.subscriptions.tally.admin.api.InternalApi;
import org.candlepin.subscriptions.tally.admin.api.model.TallyResend;
import org.candlepin.subscriptions.tally.admin.api.model.TallyResendData;
import org.candlepin.subscriptions.tally.admin.api.model.UuidList;
import org.springframework.stereotype.Component;

/** This resource is for exposing administrator REST endpoints for Tally. */
@Component
public class InternalResource implements InternalApi {

  private final MarketplaceResendTallyController resendTallyController;

  public InternalResource(MarketplaceResendTallyController resendTallyController) {
    this.resendTallyController = resendTallyController;
  }

  @Override
  public TallyResend resendTally(UuidList uuidList) {
    var tallies = resendTallyController.resendTallySnapshots(uuidList.getUuids());
    return new TallyResend().data(new TallyResendData().talliesResent(tallies));
  }
}

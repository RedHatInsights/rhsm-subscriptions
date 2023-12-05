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
package org.candlepin.subscriptions.rhmarketplace.api.admin;

import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.rhmarketplace.ApiException;
import org.candlepin.subscriptions.rhmarketplace.RhMarketplaceService;
import org.candlepin.subscriptions.rhmarketplace.admin.api.DefaultApi;
import org.candlepin.subscriptions.rhmarketplace.admin.api.model.StatusResponse;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class InternalRhMarkeplaceResource implements DefaultApi {
  private final RhMarketplaceService rhMarketplaceService;

  private final StatusResponseMapper statusResponseMapper;

  InternalRhMarkeplaceResource(
      RhMarketplaceService rhMarketplaceService, StatusResponseMapper statusMapper) {

    this.rhMarketplaceService = rhMarketplaceService;
    this.statusResponseMapper = statusMapper;
  }

  @Override
  public StatusResponse getUsageEventStatus(String batchId) {
    // NOTE: no need to validate batchId as the RHM API will do that for us
    StatusResponse response;
    try {
      response =
          statusResponseMapper.clientToApi(rhMarketplaceService.getUsageBatchStatus(batchId));
    } catch (ApiException e) {
      // pass-through of the underlying error response, see RhmExceptionMapper
      throw new RhmException(e);
    }

    return response;
  }
}

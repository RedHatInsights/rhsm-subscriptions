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
package org.candlepin.subscriptions.conduit.admin;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.conduit.InventoryController;
import org.candlepin.subscriptions.conduit.rhsm.client.ApiException;
import org.candlepin.subscriptions.exception.MissingAccountNumberException;
import org.candlepin.subscriptions.resource.ResourceUtils;
import org.candlepin.subscriptions.utilization.api.model.DefaultResponse;
import org.candlepin.subscriptions.utilization.api.model.OrgExistsResponse;
import org.candlepin.subscriptions.utilization.api.model.OrgInventory;
import org.candlepin.subscriptions.utilization.api.model.OrgSyncRequest;
import org.jboss.resteasy.spi.InternalServerErrorException;
import org.openapitools.api.InternalOrganizationsApi;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class InternalOrganizationSyncResource implements InternalOrganizationsApi {

  private final InventoryController controller;

  InternalOrganizationSyncResource(InventoryController controller) {
    this.controller = controller;
  }

  @Override
  public DefaultResponse addOrgsToSyncList(List<String> orgIds) {
    throw new UnsupportedOperationException();
  }

  @Override
  public OrgInventory getInventoryForOrg(String orgId, Integer offset, Integer limit) {
    throw new UnsupportedOperationException();
  }

  @Override
  public OrgExistsResponse hasOrgInSyncList(String orgId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public DefaultResponse removeOrgsFromSyncList(List<String> orgIds) {
    throw new UnsupportedOperationException();
  }

  @Override
  public DefaultResponse syncFullOrgList() {
    throw new UnsupportedOperationException();
  }

  @Override
  public DefaultResponse syncOrg(OrgSyncRequest orgSyncRequest) {
    log.info(
        "Starting sync for org ID {} by {}",
        orgSyncRequest.getOrgId(),
        ResourceUtils.getPrincipal());
    try {
      controller.updateInventoryForOrg(orgSyncRequest.getOrgId());
    } catch (MissingAccountNumberException | ApiException ex) {
      throw new InternalServerErrorException(ex.getMessage());
    }

    var response = new DefaultResponse();
    response.setStatus("Success");
    return response;
  }
}

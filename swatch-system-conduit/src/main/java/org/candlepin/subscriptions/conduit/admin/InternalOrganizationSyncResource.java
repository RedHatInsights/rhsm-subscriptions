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

import jakarta.ws.rs.NotFoundException;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.clock.ApplicationClock;
import org.candlepin.subscriptions.conduit.InventoryController;
import org.candlepin.subscriptions.conduit.job.OrgSyncTaskManager;
import org.candlepin.subscriptions.db.OrgConfigRepository;
import org.candlepin.subscriptions.db.model.config.OrgConfig;
import org.candlepin.subscriptions.exception.ErrorCode;
import org.candlepin.subscriptions.exception.ExternalServiceException;
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
  private final OrgSyncTaskManager tasks;
  private final OrgConfigRepository repo;
  private final ApplicationClock clock;

  private static final String SUCCESS_STATUS = "Success";

  InternalOrganizationSyncResource(
      InventoryController controller,
      OrgSyncTaskManager tasks,
      OrgConfigRepository repo,
      ApplicationClock clock) {
    this.controller = controller;
    this.tasks = tasks;
    this.repo = repo;
    this.clock = clock;
  }

  @Override
  public DefaultResponse addOrgsToSyncList(List<String> orgIds) {
    log.info(
        "Adding {} orgs to DB sync list, initiated by {}",
        orgIds.size(),
        ResourceUtils.getPrincipal());
    var orgConfigs =
        orgIds.stream()
            .map(id -> OrgConfig.fromInternalApi(id, clock.now()))
            .collect(Collectors.toList());
    repo.saveAll(orgConfigs);
    log.info(
        "Finished adding {} orgs to DB sync list, initiated by {}",
        orgIds.size(),
        ResourceUtils.getPrincipal());
    var response = new DefaultResponse();
    response.setStatus(SUCCESS_STATUS);
    return response;
  }

  @Override
  public OrgInventory getInventoryForOrg(String orgId, Integer limit, Integer offset) {
    return controller.getInventoryForOrg(orgId, offset == null ? null : offset.toString());
  }

  @Override
  public OrgExistsResponse hasOrgInSyncList(String orgId) {
    OrgExistsResponse response = new OrgExistsResponse();
    boolean exists = repo.existsById(orgId);
    response.setExistsInList(exists);
    return response;
  }

  @Override
  public DefaultResponse removeOrgsFromSyncList(List<String> orgIds) {
    throw new UnsupportedOperationException();
  }

  @Override
  public DefaultResponse syncFullOrgList() {
    log.info(
        "Starting sync for all configured orgs, initiated by {}", ResourceUtils.getPrincipal());
    tasks.syncFullOrgList();
    var response = new DefaultResponse();
    response.setStatus(SUCCESS_STATUS);
    return response;
  }

  @Override
  public DefaultResponse syncOrg(OrgSyncRequest orgSyncRequest) {
    log.info(
        "Starting sync for org ID {} by {}",
        orgSyncRequest.getOrgId(),
        ResourceUtils.getPrincipal());
    try {
      controller.updateInventoryForOrg(orgSyncRequest.getOrgId());
    } catch (ExternalServiceException ex) {
      if (ErrorCode.RHSM_SERVICE_UNKNOWN_ORG_ERROR.equals(ex.getCode())) {
        throw new NotFoundException(ex.getMessage());
      }
      throw new InternalServerErrorException(ex.getMessage());
    }

    var response = new DefaultResponse();
    response.setStatus(SUCCESS_STATUS);
    return response;
  }
}

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
package org.candlepin.subscriptions.conduit.jmx;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.candlepin.subscriptions.conduit.InventoryController;
import org.candlepin.subscriptions.conduit.job.OrgSyncTaskManager;
import org.candlepin.subscriptions.db.model.OrgConfigRepository;
import org.candlepin.subscriptions.db.model.config.OrgConfig;
import org.candlepin.subscriptions.exception.ExternalServiceException;
import org.candlepin.subscriptions.exception.MissingAccountNumberException;
import org.candlepin.subscriptions.resource.ResourceUtils;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.candlepin.subscriptions.utilization.api.model.OrgInventory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jmx.export.annotation.ManagedOperation;
import org.springframework.jmx.export.annotation.ManagedOperationParameter;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.stereotype.Component;

/** Exposes the ability to trigger a sync of a Candlepin org from JMX. */
@Component
@ManagedResource
// must log, then throw because the exception is passed to client and not logged.
@SuppressWarnings("java:S2139")
public class RhsmConduitJmxBean {

  private static final Logger log = LoggerFactory.getLogger(RhsmConduitJmxBean.class);

  private final InventoryController controller;
  private final OrgConfigRepository repo;
  private final OrgSyncTaskManager tasks;
  private final ApplicationClock clock;

  RhsmConduitJmxBean(
      InventoryController controller,
      OrgConfigRepository repo,
      OrgSyncTaskManager tasks,
      ApplicationClock clock) {
    this.controller = controller;
    this.repo = repo;
    this.tasks = tasks;
    this.clock = clock;
  }

  @ManagedOperation(description = "Trigger a sync for a given Org ID")
  public void syncOrg(String orgId) throws RhsmJmxException {
    log.info(
        "Starting JMX-initiated sync for org ID {} by {}", orgId, ResourceUtils.getPrincipal());
    try {
      controller.updateInventoryForOrg(orgId);
    } catch (ExternalServiceException e) {
      log.warn(e.getMessage());
      throw new RhsmJmxException(e.getMessage());
    } catch (Exception e) {
      log.error("Error during JMX-initiated sync for org ID {}", orgId, e);
      throw new RhsmJmxException(e);
    }
  }

  @ManagedOperation(description = "Sync all orgs from the configured org list")
  public void syncFullOrgList() throws RhsmJmxException {
    log.info(
        "Starting JMX-initiated sync for all configured orgs, initiated by {}",
        ResourceUtils.getPrincipal());
    try {
      tasks.syncFullOrgList();
    } catch (ExternalServiceException e) {
      log.warn(e.getMessage());
      throw new RhsmJmxException(e.getMessage());
    } catch (Exception e) {
      log.error("Error during JMX-initiated sync for full org list", e);
      throw new RhsmJmxException(e);
    }
  }

  @ManagedOperation(description = "Add some orgs to the database sync list")
  @ManagedOperationParameter(
      name = "orgs",
      description = "comma-separated org list (whitespace ignored)")
  public void addOrgsToSyncList(String orgs) throws RhsmJmxException {
    try {
      List<OrgConfig> orgList = extractOrgList(orgs);

      log.info(
          "Adding {} orgs to DB sync list, initiated by {}",
          orgList.size(),
          ResourceUtils.getPrincipal());

      repo.saveAll(orgList);
    } catch (Exception e) {
      log.error("Error while adding orgs to DB sync list via JMX", e);
      throw new RhsmJmxException(e);
    }
  }

  @ManagedOperation(description = "Remove some orgs from the database sync list")
  @ManagedOperationParameter(
      name = "orgs",
      description = "comma-separated org list (whitespace ignored)")
  public void removeOrgsFromSyncList(String orgs) throws RhsmJmxException {
    try {
      List<OrgConfig> orgList = extractOrgList(orgs);

      log.info(
          "Removing {} orgs from DB sync list, initiated by {}",
          orgList.size(),
          ResourceUtils.getPrincipal());

      repo.deleteAll(orgList);
    } catch (Exception e) {
      log.error("Error removing orgs from DB sync list via JMX", e);
      throw new RhsmJmxException(e);
    }
  }

  @ManagedOperation(description = "Check if an org is present in the database sync list")
  public boolean hasOrgInSyncList(String orgId) throws RhsmJmxException {
    try {
      return repo.existsById(orgId);
    } catch (Exception e) {
      log.error("Unable to determine if org {} exists via JMX", orgId);
      throw new RhsmJmxException(e);
    }
  }

  @ManagedOperation(description = "See conduit representation of a org's systems from RHSM")
  public OrgInventory getInventoryForOrg(String orgId, String offset) throws RhsmJmxException {
    try {
      return controller.getInventoryForOrg(orgId, offset);
    } catch (ExternalServiceException e) {
      log.warn(e.getMessage());
      throw new RhsmJmxException(e.getMessage());
    } catch (MissingAccountNumberException e) {
      log.error("Systems are missing account number in orgId {}", orgId);
      throw new RhsmJmxException(e);
    }
  }

  private List<OrgConfig> extractOrgList(String orgs) {
    return Arrays.stream(orgs.split("[, \n]"))
        .map(String::trim)
        .filter(orgId -> !orgId.isEmpty())
        .map(orgId -> OrgConfig.fromJmx(orgId, clock.now()))
        .collect(Collectors.toList());
  }
}

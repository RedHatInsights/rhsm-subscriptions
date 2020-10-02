/*
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
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
package org.candlepin.subscriptions.jmx;

import org.candlepin.subscriptions.controller.InventoryController;
import org.candlepin.subscriptions.db.model.OrgConfigRepository;
import org.candlepin.subscriptions.db.model.config.OrgConfig;
import org.candlepin.subscriptions.resource.ResourceUtils;
import org.candlepin.subscriptions.task.TaskManager;
import org.candlepin.subscriptions.util.ApplicationClock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.jmx.export.annotation.ManagedOperation;
import org.springframework.jmx.export.annotation.ManagedOperationParameter;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Exposes the ability to trigger a sync of a Candlepin org from JMX.
 */
@Component
@ManagedResource
@Profile("rhsm-conduit")
public class RhsmConduitJmxBean {

    private static final Logger log = LoggerFactory.getLogger(RhsmConduitJmxBean.class);

    private final InventoryController controller;
    private final OrgConfigRepository repo;
    private final TaskManager tasks;
    private final ApplicationClock clock;

    RhsmConduitJmxBean(InventoryController controller, OrgConfigRepository repo, TaskManager tasks,
        ApplicationClock clock) {
        this.controller = controller;
        this.repo = repo;
        this.tasks = tasks;
        this.clock = clock;
    }

    @ManagedOperation(description = "Trigger a sync for a given Org ID")
    public void syncOrg(String orgId) {
        log.info("Starting JMX-initiated sync for org ID {} by {}", orgId, ResourceUtils.getPrincipal());
        try {
            controller.updateInventoryForOrg(orgId);
        }
        catch (Exception e) {
            log.error("Error during JMX-initiated sync for org ID {}", orgId, e);
        }
    }

    @ManagedOperation(description = "Sync all orgs from the configured org list")
    public void syncFullOrgList() {
        log.info("Starting JMX-initiated sync for all configured orgs, initiated by {}",
            ResourceUtils.getPrincipal());
        try {
            tasks.syncFullOrgList();
        }
        catch (Exception e) {
            log.error("Error during JMX-initiated sync for full org list", e);
        }
    }

    @ManagedOperation(description = "Add some orgs to the database sync list")
    @ManagedOperationParameter(name = "orgs", description = "comma-separated org list (whitespace ignored)")
    public void addOrgsToSyncList(String orgs) {
        List<OrgConfig> orgList = extractOrgList(orgs);

        log.info("Adding {} orgs to DB sync list, initiated by {}", orgList.size(),
            ResourceUtils.getPrincipal());

        repo.saveAll(orgList);
    }

    @ManagedOperation(description = "Remove some orgs from the database sync list")
    @ManagedOperationParameter(name = "orgs", description = "comma-separated org list (whitespace ignored)")
    public void removeOrgsFromSyncList(String orgs) {
        List<OrgConfig> orgList = extractOrgList(orgs);

        log.info("Removing {} orgs from DB sync list, initiated by {}", orgList.size(),
            ResourceUtils.getPrincipal());

        repo.deleteAll(orgList);
    }

    @ManagedOperation(description = "Check if an org is present in the database sync list")
    public boolean hasOrgInSyncList(String orgId) {
        return repo.existsById(orgId);
    }

    private List<OrgConfig> extractOrgList(String orgs) {
        return Arrays.stream(orgs.split("[, \n]"))
            .map(String::trim)
            .filter(orgId -> !orgId.isEmpty())
            .map(orgId -> OrgConfig.fromJmx(orgId, clock.now()))
            .collect(Collectors.toList());
    }
}

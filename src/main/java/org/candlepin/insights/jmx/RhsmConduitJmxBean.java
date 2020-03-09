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
package org.candlepin.insights.jmx;

import org.candlepin.insights.controller.InventoryController;
import org.candlepin.insights.orgsync.db.Organization;
import org.candlepin.insights.orgsync.db.OrganizationRepository;
import org.candlepin.insights.task.TaskManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class RhsmConduitJmxBean {

    private static final Logger log = LoggerFactory.getLogger(RhsmConduitJmxBean.class);

    private final InventoryController controller;
    private final OrganizationRepository repo;
    private final TaskManager tasks;

    RhsmConduitJmxBean(InventoryController controller, OrganizationRepository repo, TaskManager tasks) {
        this.controller = controller;
        this.repo = repo;
        this.tasks = tasks;
    }

    @ManagedOperation(description = "Trigger a sync for a given Org ID")
    public void syncOrg(String orgId) {
        log.info("Starting JMX-initiated sync for org ID {}", orgId);
        try {
            controller.updateInventoryForOrg(orgId);
        }
        catch (Exception e) {
            log.error("Error during JMX-initiated sync for org ID {}", orgId, e);
        }
    }

    @ManagedOperation(description = "Sync all orgs from the configured org list")
    public void syncFullOrgList() {
        log.info("Starting JMX-initiated sync for all configured orgs");
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
        List<Organization> orgList = extractOrgList(orgs);

        log.info("Adding {} orgs to DB sync list", orgList.size());

        repo.saveAll(orgList);
    }

    @ManagedOperation(description = "Remove some orgs from the database sync list")
    @ManagedOperationParameter(name = "orgs", description = "comma-separated org list (whitespace ignored)")
    public void removeOrgsFromSyncList(String orgs) {
        List<Organization> orgList = extractOrgList(orgs);

        log.info("Removing {} orgs from DB sync list", orgList.size());

        repo.deleteAll(orgList);
    }

    @ManagedOperation(description = "Check if an org is present in the database sync list")
    public boolean hasOrgInSyncList(String orgId) {
        return repo.existsById(orgId);
    }

    private List<Organization> extractOrgList(String orgs) {
        return Arrays.stream(orgs.split("[, \n]"))
            .map(String::trim)
            .filter(orgId -> !orgId.isEmpty())
            .map(Organization::new)
            .collect(Collectors.toList());
    }
}

/*
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 * Red Hat trademarks are not licensed under GPLv2. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package org.candlepin.insights.orgsync;

import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.quartz.QuartzJobBean;

import java.io.IOException;

/**
 * A job to sync orgs from Pinhead to RHSM Conduit.
 */
public class OrgSyncJob extends QuartzJobBean {
    private static final Logger log = LoggerFactory.getLogger(OrgSyncJob.class);

    private OrgListStrategy orgListStrategy;

    @Autowired
    public OrgSyncJob(OrgListStrategy orgListStrategy) {
        this.orgListStrategy = orgListStrategy;
    }

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        try {
            log.info("{}", orgListStrategy.getOrgsToSync());
        }
        catch (IOException e) {
            throw new JobExecutionException("Job error", e);
        }
    }
}

package org.candlepin.subscriptions.rhmarketplace.billable_usage;

import org.candlepin.subscriptions.db.TagProfileLookup;
import org.candlepin.subscriptions.db.model.Host;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface BillableUsageRemittanceRepository
    extends JpaRepository<BillableUsageRemittanceEntity, BillableUsageRemittanceEntityPK>,
        JpaSpecificationExecutor<Host>,
        TagProfileLookup {}

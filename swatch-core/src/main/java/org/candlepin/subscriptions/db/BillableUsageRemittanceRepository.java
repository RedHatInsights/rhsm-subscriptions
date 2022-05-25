package org.candlepin.subscriptions.db;

import org.candlepin.subscriptions.db.model.BillableUsageRemittanceEntity;
import org.candlepin.subscriptions.db.model.BillableUsageRemittanceEntityPK;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillableUsageRemittanceRepository
    extends JpaRepository<BillableUsageRemittanceEntity, BillableUsageRemittanceEntityPK> {}

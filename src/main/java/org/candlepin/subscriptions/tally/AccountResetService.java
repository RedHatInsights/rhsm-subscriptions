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
package org.candlepin.subscriptions.tally;

import javax.transaction.Transactional;
import org.candlepin.subscriptions.db.AccountServiceInventoryRepository;
import org.candlepin.subscriptions.db.BillableUsageRemittanceRepository;
import org.candlepin.subscriptions.db.EventRecordRepository;
import org.candlepin.subscriptions.db.HostRepository;
import org.candlepin.subscriptions.db.SubscriptionMeasurementRepository;
import org.candlepin.subscriptions.db.SubscriptionRepository;
import org.candlepin.subscriptions.db.TallySnapshotRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AccountResetService {

  private final EventRecordRepository eventRecordRepo;
  private final HostRepository hostRepo;
  private final TallySnapshotRepository tallySnapshotRepository;
  private final AccountServiceInventoryRepository accountServiceInventoryRepository;
  private final SubscriptionRepository subscriptionRepository;
  private final BillableUsageRemittanceRepository remittanceRepository;
  private final SubscriptionMeasurementRepository measurementRepository;

  @Autowired
  public AccountResetService(
      EventRecordRepository eventRecordRepo,
      HostRepository hostRepo,
      TallySnapshotRepository tallySnapshotRepository,
      AccountServiceInventoryRepository accountServiceInventoryRepository,
      SubscriptionMeasurementRepository measurementRepository,
      SubscriptionRepository subscriptionRepository,
      BillableUsageRemittanceRepository remittanceRepository) {
    this.eventRecordRepo = eventRecordRepo;
    this.hostRepo = hostRepo;
    this.tallySnapshotRepository = tallySnapshotRepository;
    this.accountServiceInventoryRepository = accountServiceInventoryRepository;
    this.measurementRepository = measurementRepository;
    this.subscriptionRepository = subscriptionRepository;
    this.remittanceRepository = remittanceRepository;
  }

  @Transactional
  public void deleteDataForOrg(String orgId) {
    accountServiceInventoryRepository.deleteByIdOrgId(orgId);
    hostRepo.deleteByOrgId(orgId);
    eventRecordRepo.deleteByOrgId(orgId);
    tallySnapshotRepository.deleteByOrgId(orgId);
    subscriptionRepository.deleteByOrgId(orgId);
    measurementRepository.deleteBySubscriptionOrgId(orgId);
    remittanceRepository.deleteByKeyOrgId(orgId);
  }
}

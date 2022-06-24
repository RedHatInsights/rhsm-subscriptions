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
package org.candlepin.subscriptions.tally.billing;

import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.db.BillableUsageRemittanceRepository;
import org.candlepin.subscriptions.db.TallySnapshotRepository;
import org.candlepin.subscriptions.db.model.BillableUsageRemittanceEntityPK;
import org.candlepin.subscriptions.json.BillableUsage;
import org.candlepin.subscriptions.json.Measurement.Uom;
import org.candlepin.subscriptions.registry.BillingWindow;
import org.candlepin.subscriptions.registry.TagMetric;
import org.candlepin.subscriptions.registry.TagProfile;
import org.candlepin.subscriptions.tally.TallySummaryMapper;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
public class RemittanceController {

  private ApplicationClock clock;
  private final TagProfile tagProfile;
  private final BillableUsageRemittanceRepository remittanceRepository;
  private final TallySnapshotRepository snapshotRepository;
  private final TallySummaryMapper summaryMapper;
  private final BillableUsageMapper billableUsageMapper;
  private final BillableUsageController billableUsageController;

  public RemittanceController(
      ApplicationClock clock,
      TagProfile tagProfile,
      BillableUsageRemittanceRepository remittanceRepository,
      TallySnapshotRepository snapshotRepository,
      TallySummaryMapper summaryMapper,
      BillableUsageMapper billableUsageMapper,
      BillableUsageController billableUsageController) {
    this.clock = clock;
    this.tagProfile = tagProfile;
    this.remittanceRepository = remittanceRepository;
    this.snapshotRepository = snapshotRepository;
    this.summaryMapper = summaryMapper;
    this.billableUsageMapper = billableUsageMapper;
    this.billableUsageController = billableUsageController;
  }

  @Transactional
  public void syncRemittance() {
    log.info("Syncing remittance!");

    snapshotRepository
        .findLatestBillablesForMonth(clock.now().getMonthValue())
        .map(s -> summaryMapper.mapSnapshots(s.getAccountNumber(), List.of(s)))
        .forEach(
            summary ->
                billableUsageMapper
                    .fromTallySummary(summary)
                    .filter(
                        billable -> {
                          Optional<TagMetric> metricOptional =
                              tagProfile.getTagMetric(
                                  billable.getProductId(),
                                  Uom.fromValue(billable.getUom().value()));
                          return metricOptional.isPresent()
                              && BillingWindow.MONTHLY.equals(
                                  metricOptional.get().getBillingWindow());
                        })
                    .forEach(
                        billable -> {
                          if (remittanceExists(billable)) {
                            log.debug(
                                "Remittance already exists! Will not align remittance! {}",
                                billable);
                          } else {
                            log.info("Creating new remittance! {}", billable);
                            billableUsageController.processBillableUsage(
                                BillingWindow.MONTHLY, billable);
                          }
                        }));
  }

  private boolean remittanceExists(BillableUsage billableUsage) {
    return remittanceRepository.existsById(BillableUsageRemittanceEntityPK.keyFrom(billableUsage));
  }
}

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

import io.micrometer.core.annotation.Timed;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.candlepin.subscriptions.ApplicationProperties;
import org.candlepin.subscriptions.db.AccountConfigRepository;
import org.candlepin.subscriptions.db.model.Granularity;
import org.candlepin.subscriptions.db.model.TallySnapshot;
import org.candlepin.subscriptions.exception.ErrorCode;
import org.candlepin.subscriptions.exception.ExternalServiceException;
import org.candlepin.subscriptions.registry.TagProfile;
import org.candlepin.subscriptions.util.DateRange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Provides the logic for updating Tally snapshots. */
@Component
public class TallySnapshotController {

  private static final Logger log = LoggerFactory.getLogger(TallySnapshotController.class);

  private final ApplicationProperties props;
  private final AccountConfigRepository accountRepo;
  private final InventoryAccountUsageCollector usageCollector;
  private final CloudigradeAccountUsageCollector cloudigradeCollector;
  private final MetricUsageCollector metricUsageCollector;
  private final MaxSeenSnapshotStrategy maxSeenSnapshotStrategy;
  private final CombiningRollupSnapshotStrategy combiningRollupSnapshotStrategy;
  private final RetryTemplate retryTemplate;
  private final RetryTemplate cloudigradeRetryTemplate;
  private final Set<String> applicableProducts;
  private final TagProfile tagProfile;
  private final SnapshotSummaryProducer summaryProducer;

  @Autowired
  public TallySnapshotController(
      ApplicationProperties props,
      AccountConfigRepository accountRepo,
      @Qualifier("applicableProducts") Set<String> applicableProducts,
      InventoryAccountUsageCollector usageCollector,
      CloudigradeAccountUsageCollector cloudigradeCollector,
      MaxSeenSnapshotStrategy maxSeenSnapshotStrategy,
      @Qualifier("collectorRetryTemplate") RetryTemplate retryTemplate,
      @Qualifier("cloudigradeRetryTemplate") RetryTemplate cloudigradeRetryTemplate,
      MetricUsageCollector metricUsageCollector,
      CombiningRollupSnapshotStrategy combiningRollupSnapshotStrategy,
      TagProfile tagProfile,
      SnapshotSummaryProducer summaryProducer) {

    this.props = props;
    this.accountRepo = accountRepo;
    this.applicableProducts = applicableProducts;
    this.usageCollector = usageCollector;
    this.cloudigradeCollector = cloudigradeCollector;
    this.maxSeenSnapshotStrategy = maxSeenSnapshotStrategy;
    this.retryTemplate = retryTemplate;
    this.cloudigradeRetryTemplate = cloudigradeRetryTemplate;
    this.metricUsageCollector = metricUsageCollector;
    this.combiningRollupSnapshotStrategy = combiningRollupSnapshotStrategy;
    this.tagProfile = tagProfile;
    this.summaryProducer = summaryProducer;
  }

  // SWATCH-614 Deprecate this method after org id migration
  @Timed("rhsm-subscriptions.snapshots.single")
  public void produceSnapshotsForAccount(String account) {
    produceSnapshotsForOrg(accountRepo.findOrgByAccountNumber(account));
  }

  @Timed("rhsm-subscriptions.snapshots.single")
  public void produceSnapshotsForOrg(String orgId) {
    if (Objects.isNull(orgId)) {
      throw new IllegalArgumentException("A non-null orgId is required for tally operations.");
    }

    String account = accountRepo.findAccountNumberByOrgId(orgId);
    log.info("Producing snapshots for Org ID {} with Account {}.", orgId, account);
    AccountUsageCalculation accountCalc;
    OrgHostsData orgHostsData;
    try {
      orgHostsData =
          retryTemplate.execute(
              context -> {
                try {
                  return usageCollector.collect(this.applicableProducts, account, orgId);
                } catch (SystemThresholdException e) {
                  log.warn(e.getMessage());
                  return null;
                }
              });
      if (Objects.isNull(orgHostsData)) { // orgHostsData is null when the threshold is breached
        return;
      }

      accountCalc = usageCollector.tally(this.applicableProducts, orgHostsData, account, orgId);
      if (props.isCloudigradeEnabled()) {
        attemptCloudigradeEnrichment(accountCalc);
      }
    } catch (Exception e) {
      log.error(
          "Could not collect existing usage snapshots for orgId={} account={}", orgId, account, e);
      return;
    }

    maxSeenSnapshotStrategy.produceSnapshotsFromCalculations(accountCalc);
  }

  // Because we want to ensure that our DB operations have been completed before
  // any messages are sent, message sending must be done outside a DB transaction
  // boundary. We use Propagation.NEVER here so that if this method should ever be called
  // from within an existing DB transaction, an exception will be thrown.
  @Transactional(propagation = Propagation.NEVER)
  @Timed("rhsm-subscriptions.snapshots.single.hourly")
  public void produceHourlySnapshotsForOrg(String orgId, DateRange snapshotRange) {
    if (Objects.isNull(orgId)) {
      throw new IllegalArgumentException("A non-null orgId is required for tally operations.");
    }

    String accountNumber = accountRepo.findAccountNumberByOrgId(orgId);
    log.info("Producing snapshots for Org ID {} with Account {}.", orgId, accountNumber);
    tagProfile
        .getServiceTypes()
        .forEach(
            serviceType -> {
              log.info(
                  "Producing hourly snapshots for orgId {} for service type {} "
                      + "between startDateTime {} and endDateTime {}",
                  orgId,
                  serviceType,
                  snapshotRange.getStartString(),
                  snapshotRange.getEndString());
              try {
                var result =
                    retryTemplate.execute(
                        context ->
                            metricUsageCollector.collect(
                                serviceType, accountNumber, orgId, snapshotRange));
                if (result == null) {
                  return;
                }

                var applicableUsageCalculations =
                    result.getCalculations().entrySet().stream()
                        .filter(this::isCombiningRollupStrategySupported)
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                Map<String, List<TallySnapshot>> totalSnapshots =
                    combiningRollupSnapshotStrategy.produceSnapshotsFromCalculations(
                        orgId,
                        result.getRange(),
                        tagProfile.getTagsForServiceType(serviceType),
                        applicableUsageCalculations,
                        Granularity.HOURLY,
                        Double::sum);

                summaryProducer.produceTallySummaryMessages(totalSnapshots);
                log.info(
                    "Finished producing hourly snapshots for account {} with orgId {}",
                    accountNumber,
                    orgId);
              } catch (Exception e) {
                log.error(
                    "Could not collect metrics and/or produce snapshots for account {} with orgId {}",
                    accountNumber,
                    orgId,
                    e);
              }
            });
  }

  private void attemptCloudigradeEnrichment(AccountUsageCalculation accountCalc) {
    log.info("Adding cloudigrade reports to calculations.");
    try {
      cloudigradeRetryTemplate.execute(
          context -> {
            try {
              cloudigradeCollector.enrichUsageWithCloudigradeData(accountCalc);
            } catch (Exception e) {
              throw new ExternalServiceException(
                  ErrorCode.REQUEST_PROCESSING_ERROR,
                  "Error during attempt to integrate cloudigrade report",
                  e);
            }
            return null; // RetryCallback requires a return
          });
    } catch (Exception e) {
      log.warn("Exception during cloudigrade enrichment, tally will not be enriched.", e);
    }
  }

  private boolean isCombiningRollupStrategySupported(
      Map.Entry<OffsetDateTime, AccountUsageCalculation> usageCalculations) {

    var calculatedProducts = usageCalculations.getValue().getProducts();
    return calculatedProducts.stream().anyMatch(tagProfile::isProductPAYGEligible);
  }
}

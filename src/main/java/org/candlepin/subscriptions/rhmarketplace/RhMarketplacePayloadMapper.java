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
package org.candlepin.subscriptions.rhmarketplace;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.exception.ErrorCode;
import org.candlepin.subscriptions.json.BillableUsage;
import org.candlepin.subscriptions.json.TallyMeasurement.Uom;
import org.candlepin.subscriptions.json.TallySummary;
import org.candlepin.subscriptions.registry.TagProfile;
import org.candlepin.subscriptions.rhmarketplace.api.model.UsageEvent;
import org.candlepin.subscriptions.rhmarketplace.api.model.UsageMeasurement;
import org.candlepin.subscriptions.rhmarketplace.api.model.UsageRequest;
import org.candlepin.subscriptions.tally.UsageCalculation;
import org.candlepin.subscriptions.tally.billing.BillableUsageMapper;
import org.candlepin.subscriptions.user.AccountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Maps BillableUsage to payload contents to be sent to RHM apis */
@Service
public class RhMarketplacePayloadMapper {
  private static final Logger log = LoggerFactory.getLogger(RhMarketplacePayloadMapper.class);

  public static final String OPENSHIFT_DEDICATED_4_CPU_HOUR =
      "redhat.com:openshift_dedicated:4cpu_hour";

  private final AccountService accountService;
  private final RhMarketplaceSubscriptionIdProvider idProvider;
  private final BillableUsageMapper billableUsageMapper;
  private final TagProfile tagProfile;

  @Autowired
  public RhMarketplacePayloadMapper(
      TagProfile tagProfile,
      AccountService accountService,
      RhMarketplaceSubscriptionIdProvider idProvider) {
    this.tagProfile = tagProfile;
    this.accountService = accountService;
    this.idProvider = idProvider;
    // NOTE(khowell) this dependency is temporary, and instantiating here was easier than
    // refactoring profiles.
    this.billableUsageMapper = new BillableUsageMapper(tagProfile);
  }

  /**
   * Create UsageRequest pojo to send to Marketplace
   *
   * @param billableUsage BillableUsage
   * @return UsageRequest
   */
  public UsageRequest createUsageRequest(BillableUsage billableUsage) {

    UsageRequest usageRequest = new UsageRequest();
    usageRequest.setData(
        Optional.ofNullable(produceUsageEvent(billableUsage)).map(List::of).orElse(List.of()));

    log.debug("UsageRequest {}", usageRequest);

    return usageRequest;
  }

  protected boolean isUsageRHMarketplaceEligible(BillableUsage usage) {
    // ANY, __EMPTY__ are already filtered, will check null just to be safe.
    return Objects.nonNull(usage.getBillingProvider())
        && usage.getBillingProvider().equals(BillableUsage.BillingProvider.RED_HAT);
  }

  /**
   * UsageRequest objects are made up of a list of UsageEvents.
   *
   * @param billableUsage BillableUsage
   * @return List&lt;UsageEvent&gt;
   */
  protected UsageEvent produceUsageEvent(BillableUsage billableUsage) {
    if (!isValid(billableUsage)) {
      log.warn("Skipping invalid billable usage {}", billableUsage);
      return null;
    }
    String accountNumber = billableUsage.getAccountNumber();
    String orgId = billableUsage.getOrgId();
    if (orgId == null) {
      orgId = accountService.lookupOrgId(accountNumber);
    }

    String productId = billableUsage.getProductId();
    // Use "_ANY" because we don't support multiple rh marketplace accounts for a single customer
    String billingAcctId = "_ANY";

    UsageCalculation.Key usageKey =
        new UsageCalculation.Key(
            productId,
            ServiceLevel.fromString(billableUsage.getSla().toString()),
            Usage.fromString(billableUsage.getUsage().toString()),
            BillingProvider.fromString(billableUsage.getBillingProvider().toString()),
            billingAcctId);

    OffsetDateTime snapshotDate = billableUsage.getSnapshotDate();
    String eventId = billableUsage.getId().toString();

    /*
    This will need to be updated if we expand the criteria defined in the
    isSnapshotPAYGEligible method to allow for Granularities other than HOURLY
     */
    long start = snapshotDate.toInstant().toEpochMilli();
    long end = snapshotDate.plus(Duration.ofHours(1L)).toInstant().toEpochMilli();

    var subscriptionIdOpt =
        idProvider.findSubscriptionId(accountNumber, orgId, usageKey, snapshotDate, snapshotDate);

    if (subscriptionIdOpt.isEmpty()) {
      log.error("{}", ErrorCode.SUBSCRIPTION_SERVICE_MARKETPLACE_ID_LOOKUP_ERROR);
      return null;
    }

    var usageMeasurement = produceUsageMeasurement(billableUsage);

    return new UsageEvent()
        .measuredUsage(List.of(usageMeasurement))
        .end(end)
        .start(start)
        .subscriptionId(subscriptionIdOpt.get())
        .eventId(eventId)
        .additionalAttributes(Collections.emptyMap());
  }

  /**
   * UsageEvents include a list of usage measurements. This data includes unit of measure (UOM), the
   * value for the uom, and the rhmMetricId (RHM terminology) which is a configuration value of the
   * product the uom is for.
   *
   * @param billableUsage billable usage to transform
   * @return List&lt;UsageMeasurement%gt;
   */
  protected UsageMeasurement produceUsageMeasurement(BillableUsage billableUsage) {
    Uom uom = Uom.fromValue(billableUsage.getUom().value());
    String rhmMarketplaceMetricId =
        tagProfile.rhmMetricIdForTagAndUom(billableUsage.getProductId(), uom);
    Double value = billableUsage.getValue();

    // RHM is expecting counts of 4 vCPU-hour blocks, but currently does not have a way
    // to do this automatically. If we detect this case, divide the cores value by 4.
    //
    // We need a longer term process to get that information onto the SKU/product
    // definition
    // itself so that we are not hard coding this type of value in our code. This will do
    // for now. Wll be removed eventually in Phase 3 of SWATCH-582 subtasks
    if (OPENSHIFT_DEDICATED_4_CPU_HOUR.equalsIgnoreCase(rhmMarketplaceMetricId)
        && (billableUsage.getBillingFactor() == 1.0 || billableUsage.getBillingFactor() == null)
        && !Objects.isNull(value)
        && Uom.CORES.equals(uom)) {
      value = value / 4;
      log.debug(
          "Found cores measurement for metric ID {}. Dividing cores by 4: {}",
          OPENSHIFT_DEDICATED_4_CPU_HOUR,
          value);
    }

    UsageMeasurement usageMeasurement = new UsageMeasurement();
    usageMeasurement.setValue(value);
    usageMeasurement.setMetricId(rhmMarketplaceMetricId);
    return usageMeasurement;
  }

  private boolean isValid(BillableUsage billableUsage) {
    return billableUsage.getProductId() != null
        && billableUsage.getSla() != null
        && billableUsage.getUsage() != null
        && billableUsage.getUom() != null
        && billableUsage.getBillingProvider() != null
        && billableUsage.getBillingAccountId() != null
        && billableUsage.getSnapshotDate() != null
        && billableUsage.getId() != null
        && isUsageRHMarketplaceEligible(billableUsage);
  }

  public Stream<UsageRequest> createUsageRequests(TallySummary tallySummary) {
    return billableUsageMapper.fromTallySummary(tallySummary).map(this::createUsageRequest);
  }
}

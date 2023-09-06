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

import com.redhat.swatch.clients.internal.subscriptions.api.client.ApiException;
import com.redhat.swatch.clients.internal.subscriptions.api.model.RhmUsageContext;
import com.redhat.swatch.clients.internal.subscriptions.api.resources.InternalSubscriptionsApi;
import com.redhat.swatch.configuration.registry.SubscriptionDefinition;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import org.candlepin.subscriptions.exception.ErrorCode;
import org.candlepin.subscriptions.json.BillableUsage;
import org.candlepin.subscriptions.json.BillableUsage.Sla;
import org.candlepin.subscriptions.json.BillableUsage.Usage;
import org.candlepin.subscriptions.json.TallySummary;
import org.candlepin.subscriptions.rhmarketplace.api.model.UsageEvent;
import org.candlepin.subscriptions.rhmarketplace.api.model.UsageMeasurement;
import org.candlepin.subscriptions.rhmarketplace.api.model.UsageRequest;
import org.candlepin.subscriptions.tally.billing.BillableUsageMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** Maps BillableUsage to payload contents to be sent to RHM apis */
@Service
public class RhMarketplacePayloadMapper {
  private static final Logger log = LoggerFactory.getLogger(RhMarketplacePayloadMapper.class);

  public static final String OPENSHIFT_DEDICATED_4_CPU_HOUR =
      "redhat.com:openshift_dedicated:4cpu_hour";

  private final BillableUsageMapper billableUsageMapper;
  private final InternalSubscriptionsApi subscriptionsClient;
  private final RetryTemplate usageContextRetryTemplate;

  @Autowired
  public RhMarketplacePayloadMapper(
      InternalSubscriptionsApi subscriptionsClient,
      @Qualifier("rhmUsageContextLookupRetryTemplate") RetryTemplate usageContextRetryTemplate) {
    this.subscriptionsClient = subscriptionsClient;
    // NOTE(khowell) this dependency is temporary, and instantiating here was easier than
    // refactoring profiles.
    this.billableUsageMapper = new BillableUsageMapper();
    this.usageContextRetryTemplate = usageContextRetryTemplate;
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

    OffsetDateTime snapshotDate = billableUsage.getSnapshotDate();
    String eventId = billableUsage.getId().toString();

    /*
    This will need to be updated if we expand the criteria defined in the
    isSnapshotPAYGEligible method to allow for Granularities other than HOURLY
     */
    long start = snapshotDate.toInstant().toEpochMilli();
    long end = snapshotDate.plus(Duration.ofHours(1L)).toInstant().toEpochMilli();

    RhmUsageContext context = null;
    try {
      context = lookupRhmUsageContext(billableUsage);
    } catch (RhmUsageContextLookupException e) {
      log.error(e.getMessage());
      return null;
    }

    if (Objects.isNull(context) || !StringUtils.hasText(context.getRhSubscriptionId())) {
      log.error("{}", ErrorCode.SUBSCRIPTION_SERVICE_MARKETPLACE_ID_LOOKUP_ERROR);
      return null;
    }

    var usageMeasurement = produceUsageMeasurement(billableUsage);

    return new UsageEvent()
        .measuredUsage(List.of(usageMeasurement))
        .end(end)
        .start(start)
        .subscriptionId(context.getRhSubscriptionId())
        .eventId(eventId)
        .additionalAttributes(Collections.emptyMap());
  }

  public RhmUsageContext lookupRhmUsageContext(BillableUsage billableUsage)
      throws RhmUsageContextLookupException {
    return usageContextRetryTemplate.execute(
        context -> {
          String accountNumber = billableUsage.getAccountNumber();
          String orgId = billableUsage.getOrgId();
          try {
            log.debug(
                "Looking up RHM usage context for orgId={} billableUsage={}", orgId, billableUsage);
            return subscriptionsClient.getRhmUsageContext(
                orgId,
                billableUsage.getSnapshotDate(),
                billableUsage.getProductId(),
                accountNumber,
                Optional.ofNullable(billableUsage.getSla()).map(Sla::value).orElse(null),
                Optional.ofNullable(billableUsage.getUsage()).map(Usage::value).orElse(null));
          } catch (ApiException e) {
            throw new RhmUsageContextLookupException(
                ErrorCode.SUBSCRIPTION_SERVICE_MARKETPLACE_ID_LOOKUP_ERROR,
                String.format("API returned code %d", e.getCode()));
          }
        });
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

    String rhmMarketplaceMetricId =
        SubscriptionDefinition.getRhmMetricId(billableUsage.getProductId(), billableUsage.getUom());

    Double value = billableUsage.getValue();

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

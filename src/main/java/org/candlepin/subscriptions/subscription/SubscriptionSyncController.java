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
package org.candlepin.subscriptions.subscription;

import com.redhat.swatch.configuration.registry.Variant;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.db.SubscriptionRepository;
import org.candlepin.subscriptions.db.model.DbReportCriteria;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Subscription_;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.exception.ErrorCode;
import org.candlepin.subscriptions.exception.SubscriptionsException;
import org.candlepin.subscriptions.tally.UsageCalculation.Key;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

/** Update subscriptions from subscription service responses. */
@Component
@Slf4j
public class SubscriptionSyncController {
  private final SubscriptionRepository subscriptionRepository;

  @Autowired
  public SubscriptionSyncController(SubscriptionRepository subscriptionRepository) {
    this.subscriptionRepository = subscriptionRepository;
  }

  @Transactional
  public String terminateSubscription(String subscriptionId, OffsetDateTime terminationDate) {
    var subscriptions = subscriptionRepository.findActiveSubscription(subscriptionId);
    if (subscriptions.isEmpty()) {
      throw new EntityNotFoundException(
          String.format(
              "Cannot terminate subscription because no active subscription was found with subscription ID '%s'",
              subscriptionId));
    } else if (subscriptions.size() > 1) {
      throw new SubscriptionsException(
          ErrorCode.UNHANDLED_EXCEPTION_ERROR,
          Response.Status.INTERNAL_SERVER_ERROR,
          "Multiple active subscription found",
          String.format(
              "Cannot terminate subscription because multiple active subscriptions were found for subscription ID '%s'",
              subscriptionId));
    }

    var subscription = subscriptions.get(0);

    // Wait until after we are sure there's an offering for this subscription before setting the
    // end date.  We want validation to occur before we start mutating data.
    subscription.setEndDate(terminationDate);

    OffsetDateTime now = OffsetDateTime.now();
    // The calculation returns a whole number, representing the number of complete units
    // between the two temporals. For example, the amount in hours between the times 11:30 and
    // 12:29 will zero hours as it is one minute short of an hour.
    var delta = Math.abs(ChronoUnit.HOURS.between(terminationDate, now));
    if (subscription.getOffering().isMetered() && delta > 0) {
      var msg =
          String.format(
              "Subscription %s terminated at %s with out of range termination date %s.",
              subscriptionId, now, terminationDate);
      log.warn(msg);
      return msg;
    }
    return String.format("Subscription %s terminated at %s.", subscriptionId, terminationDate);
  }

  @Transactional
  public List<org.candlepin.subscriptions.db.model.Subscription> findSubscriptions(
      String orgId, Key usageKey, OffsetDateTime rangeStart, OffsetDateTime rangeEnd) {
    Assert.isTrue(Usage._ANY != usageKey.getUsage(), "Usage cannot be _ANY");
    Assert.isTrue(ServiceLevel._ANY != usageKey.getSla(), "Service Level cannot be _ANY");

    String productTag = usageKey.getProductId();
    if (!Variant.isValidProductTag(productTag)) {
      log.warn("No product tag configured: {}", productTag);
      return Collections.emptyList();
    }

    DbReportCriteria.DbReportCriteriaBuilder reportCriteriaBuilder =
        DbReportCriteria.builder()
            .productTag(productTag)
            .serviceLevel(ServiceLevel._ANY)
            // NOTE(khowell) due to an oversight PAYG SKUs don't currently have a usage set -
            // at some point we should use usageKey.getUsage() instead of "_ANY"
            .usage(Usage._ANY)
            .billingProvider(usageKey.getBillingProvider())
            .billingAccountId(usageKey.getBillingAccountId())
            .payg(true)
            .beginning(rangeStart)
            .ending(rangeEnd)
            .orgId(orgId);

    DbReportCriteria subscriptionCriteria = reportCriteriaBuilder.build();

    List<org.candlepin.subscriptions.db.model.Subscription> result =
        subscriptionRepository.findByCriteria(
            subscriptionCriteria, Sort.by(Subscription_.START_DATE).descending());

    if (result.isEmpty()) {
      log.error("No subscription found for orgId {} with criteria {}", orgId, subscriptionCriteria);
    }

    return result;
  }
}

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
package com.redhat.swatch.billable.usage.admin.api;

import com.redhat.swatch.billable.usage.configuration.ApplicationConfiguration;
import com.redhat.swatch.billable.usage.data.BillableUsageRemittanceFilter;
import com.redhat.swatch.billable.usage.kafka.streams.FlushTopicService;
import com.redhat.swatch.billable.usage.openapi.model.DefaultResponse;
import com.redhat.swatch.billable.usage.openapi.model.MonthlyRemittance;
import com.redhat.swatch.billable.usage.openapi.model.TallyRemittance;
import com.redhat.swatch.billable.usage.openapi.resource.DefaultApi;
import com.redhat.swatch.billable.usage.services.EnabledOrgsProducer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ProcessingException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
@AllArgsConstructor
public class InternalBillableUsageResource implements DefaultApi {

  private static final String SUCCESS_STATUS = "Success";
  private static final String REJECTED_STATUS = "Rejected";

  private final FlushTopicService flushTopicService;
  private final InternalBillableUsageController billingController;
  private final EnabledOrgsProducer enabledOrgsProducer;
  private final ApplicationConfiguration configuration;

  @Override
  public List<MonthlyRemittance> getRemittances(
      String productId,
      String orgId,
      String metricId,
      String billingProvider,
      String billingAccountId,
      OffsetDateTime beginning,
      OffsetDateTime ending) {

    if (Objects.isNull(orgId)) {
      throw new BadRequestException("Must provide 'orgId' query parameters.");
    }

    if (Objects.nonNull(beginning) && Objects.nonNull(ending) && beginning.isAfter(ending)) {
      throw new BadRequestException("Query parameter 'beginning' must be before 'ending'.");
    }

    BillableUsageRemittanceFilter filter =
        BillableUsageRemittanceFilter.builder()
            .orgId(orgId)
            .productId(productId)
            .metricId(metricId)
            .billingProvider(billingProvider)
            .billingAccountId(billingAccountId)
            .beginning(beginning)
            .ending(ending)
            .build();
    return billingController.getRemittances(filter);
  }

  @Override
  public List<TallyRemittance> getRemittancesByTally(String tallyId) throws ProcessingException {

    BillableUsageRemittanceFilter filter =
        BillableUsageRemittanceFilter.builder().tallyId(UUID.fromString(tallyId)).build();
    List<TallyRemittance> tallyRemittances = billingController.getRemittancesByTally(filter);
    if (tallyRemittances.isEmpty()) {
      throw new BadRequestException("Tally id not found in billable usage remittance" + tallyId);
    }
    return tallyRemittances;
  }

  @Override
  public DefaultResponse resetBillableUsageRemittance(
      String productId,
      OffsetDateTime start,
      OffsetDateTime end,
      Set<String> orgIds,
      Set<String> billingAccountIds) {
    if ((orgIds == null || orgIds.isEmpty())
        && (billingAccountIds == null || billingAccountIds.isEmpty())) {
      throw new BadRequestException("Either orgIds or billingAccountIds must be provided.");
    } else if (orgIds != null
        && !orgIds.isEmpty()
        && billingAccountIds != null
        && !billingAccountIds.isEmpty()) {
      throw new BadRequestException(
          "Only one of orgIds or billingAccountIds parameters should be specified at a time, do not provide both.");
    }
    int updatedRemittance;
    try {
      updatedRemittance =
          billingController.resetBillableUsageRemittance(
              productId, start, end, orgIds, billingAccountIds);
    } catch (Exception e) {
      log.warn("Billable usage remittance update failed.", e);
      return getDefaultResponse(REJECTED_STATUS);
    }
    if (updatedRemittance > 0) {
      return new DefaultResponse().status(SUCCESS_STATUS);
    } else {
      throw new BadRequestException(
          String.format(
              "No record found for billable usage remittance for productId %s and between start %s and end date %s and orgIds %s or billingAccountIds %s",
              productId, start, end, orgIds, billingAccountIds));
    }
  }

  @Override
  public DefaultResponse flushBillableUsageAggregationTopic() throws ProcessingException {
    flushTopicService.sendFlushToBillableUsageRepartitionTopic();
    return new DefaultResponse().status(SUCCESS_STATUS);
  }

  @Override
  public DefaultResponse deleteRemittancesAssociatedWithOrg(String orgId)
      throws ProcessingException {
    billingController.deleteDataForOrg(orgId);
    return getDefaultResponse(SUCCESS_STATUS);
  }

  @Override
  public DefaultResponse purgeRemittances() {
    var policyDuration = configuration.getRemittanceRetentionPolicyDuration();
    if (policyDuration == null) {
      log.warn(
          "Purging remittances won't be done because the policy duration is not configured. "
              + "You can configure it by using `rhsm-subscriptions.remittance-retention-policy.duration`.");
      return getDefaultResponse(REJECTED_STATUS);
    }

    enabledOrgsProducer.sendTaskForRemittancesPurgeTask();
    return getDefaultResponse(SUCCESS_STATUS);
  }

  @Override
  public DefaultResponse reconcileRemittances() {
    var policyDuration = configuration.getRemittanceStatusStuckDuration();

    billingController.reconcileBillableUsageRemittances(policyDuration.toDays());
    return getDefaultResponse(SUCCESS_STATUS);
  }

  private DefaultResponse getDefaultResponse(String status) {
    var response = new DefaultResponse();
    response.setStatus(status);
    return response;
  }
}

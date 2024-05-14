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

import com.redhat.swatch.billable.usage.kafka.streams.FlushTopicService;
import com.redhat.swatch.billable.usage.openapi.model.DefaultResponse;
import com.redhat.swatch.billable.usage.openapi.resource.DefaultApi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.ProcessingException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
@AllArgsConstructor
public class InternalBillableUsageResource implements DefaultApi {

  private static final String SUCCESS_STATUS = "Success";

  private final FlushTopicService flushTopicService;
  private final InternalBillableUsageController billingController;

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

  private DefaultResponse getDefaultResponse(String status) {
    var response = new DefaultResponse();
    response.setStatus(status);
    return response;
  }
}

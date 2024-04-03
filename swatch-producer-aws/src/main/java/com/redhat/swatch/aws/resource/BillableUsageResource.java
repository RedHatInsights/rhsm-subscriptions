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
package com.redhat.swatch.aws.resource;

import com.redhat.swatch.aws.exception.AwsManualSubmissionDisabledException;
import com.redhat.swatch.aws.openapi.resource.DefaultApi;
import com.redhat.swatch.aws.processors.BillableUsageProducer;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.billable.usage.BillableUsage;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Slf4j
public class BillableUsageResource implements DefaultApi {
  private final BillableUsageProducer tallySummaryProducer;
  private final boolean manualSubmissionEnabled;

  BillableUsageResource(
      BillableUsageProducer tallySummaryProducer,
      @ConfigProperty(name = "AWS_MANUAL_SUBMISSION_ENABLED") boolean manualSubmissionEnabled) {
    this.tallySummaryProducer = tallySummaryProducer;
    this.manualSubmissionEnabled = manualSubmissionEnabled;
  }

  @Override
  public void submitBillableUsage(BillableUsage billableUsage) {
    if (!manualSubmissionEnabled) {
      throw new AwsManualSubmissionDisabledException();
    }
    log.info("{}", billableUsage);

    tallySummaryProducer.queueBillableUsage(billableUsage);
  }
}

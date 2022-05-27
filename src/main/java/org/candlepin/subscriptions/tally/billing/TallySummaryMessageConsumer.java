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

import io.micrometer.core.annotation.Timed;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.json.TallySummary;
import org.candlepin.subscriptions.task.TaskQueueProperties;
import org.candlepin.subscriptions.util.KafkaConsumerRegistry;
import org.candlepin.subscriptions.util.SeekableKafkaConsumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/*
 * Processes messages on the TallySummary topic and delegates to the BillingProducer for processing.
 */
@Service
@Slf4j
public class TallySummaryMessageConsumer extends SeekableKafkaConsumer {

  private BillingProducer billingProducer;
  private BillableUsageEvaluator billableUsageEvaluator;

  @Autowired
  public TallySummaryMessageConsumer(
      BillingProducer billingProducer,
      @Qualifier("billingProducerTallySummaryTopicProperties")
          TaskQueueProperties tallySummaryTopicProperties,
      KafkaConsumerRegistry kafkaConsumerRegistry,
      BillableUsageEvaluator billableUsageEvaluator) {
    super(tallySummaryTopicProperties, kafkaConsumerRegistry);
    this.billingProducer = billingProducer;
    this.billableUsageEvaluator = billableUsageEvaluator;
  }

  @Timed("rhsm-subscriptions.billing-producer.tally-summary")
  @KafkaListener(
      id = "#{__listener.groupId}",
      topics = "#{__listener.topic}",
      containerFactory = "billingProducerKafkaTallySummaryListenerContainerFactory")
  public void receive(TallySummary tallySummary) {
    log.debug("Tally Summary received. Producing billable usage.}");

    /*
            {
              "account_number": "account123",
              "tally_snapshots": [
                {
                  "id": "2b1ca6a5-2afe-4d84-9379-8a6d28a4b781",
                  "billing_provider": "red hat",
                  "billing_account_id": "456",
                  "snapshot_date": 1652911085.53094,
                  "product_id": "rhosak",
                  "sla": "Premium",
                  "usage": "Production",
                  "granularity": "Hourly",
                  "tally_measurements": [{ "uom": "Cores", "value": 1.0 }]
                }
              ]
        }
    */

    // Do i process this message?  We only want to process hourly granularity, and we don't want to
    // process roll ups

    // Prereq: This is a concrete piece of hourly usage

    // Check tag profile flag from 5011, when we're not tracking monthly (false):
    // forward tally summary to billable usage to the new topic

    // if we ARE tracking monthly (true)

    // Load from the tracking table by key

    // we need two pieces of information: what have we emitted thus far (from tracking table)?
    // single record that gets updated (double), and the cumulative usage since hte beginning of hte
    // month

    // Do we have to do some new calculation? if AWS, yes.  if RHM, no.

    billableUsageEvaluator.expandIntoBillingUsageRemittanceEntities(tallySummary).stream()
        .forEach(
            thing -> {

              // always emit billableusage, even if amount is zero.  the consumers of these messages
              // will decide if they want to process zeroes.

              this.billingProducer.produce(
                  billableUsageEvaluator.transformMeToBillableUsage(tallySummary));

              billableUsageEvaluator.saveATrackingTableThing(thing);
            });
  }
}

// TODO:

// - update BillableUsageRemittance entity to use org_id instead of account_id.  update queries
// accordingly and accept both account_number and org_id - prefer org_id first.  Add another db
// column for org_id

// - keep granularity in the key so we can support other (non-monthly) windows.  "month" column
// should be renamed to like ?accumulation_period?...when/if we do weekly, we would come up with
// some kind of identifier instead of YYYY-MM

// - BillableUsage message to be updated to basically mirror the BillableUsageRemittance entity
// rather than shoving it back into a List<TallySummary> -> List<Measurements> model like it came in
// as being a Tally Summary.  BillableUsageEvaluator.transformMeToBillableUsage is where this
// transformation should happen

// TODO how to handle retally...pass through tally snapshot id

// TODO how to recover from if we logged to the tracking table a remittance value, but the call to
// the billing provider failed downstream.  how much do we bill for the next time around?

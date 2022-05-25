package org.candlepin.subscriptions.rhmarketplace.billable_usage;

import io.micrometer.core.annotation.Timed;
import java.util.Optional;
import org.candlepin.subscriptions.json.TallySummary;
import org.candlepin.subscriptions.task.TaskQueueProperties;
import org.candlepin.subscriptions.util.KafkaConsumerRegistry;
import org.candlepin.subscriptions.util.SeekableKafkaConsumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class BillingUsageEvaluator extends SeekableKafkaConsumer {

  private final BillableUsageRemittanceRepository billableUsageRemittanceRepository;

  @Autowired
  BillingUsageEvaluator(
      @Qualifier("rhMarketplaceTasks") TaskQueueProperties taskQueueProperties,
      KafkaConsumerRegistry kafkaConsumerRegistry,
      BillableUsageRemittanceRepository billableUsageRemittanceRepository) {
    super(taskQueueProperties, kafkaConsumerRegistry);
    this.billableUsageRemittanceRepository = billableUsageRemittanceRepository;
  }

  @Timed("rhsm-subscriptions.marketplace.tally-summary")
  @KafkaListener(
      id = "#{__listener.groupId}",
      autoStartup = "#{__listener.enabled}",
      topics = "#{__listener.topic}",
      containerFactory = "kafkaTallySummaryListenerContainerFactory")
  public void receive(TallySummary tallySummary) {

    System.out.println(tallySummary);
  }
}

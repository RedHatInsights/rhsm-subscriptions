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

import static java.util.stream.Collectors.toMap;

import jakarta.transaction.Transactional;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.RecordsToDelete;
import org.apache.kafka.common.TopicPartition;
import org.candlepin.subscriptions.db.AccountServiceInventoryRepository;
import org.candlepin.subscriptions.db.BillableUsageRemittanceRepository;
import org.candlepin.subscriptions.db.EventRecordRepository;
import org.candlepin.subscriptions.db.HostRepository;
import org.candlepin.subscriptions.db.SubscriptionRepository;
import org.candlepin.subscriptions.db.TallySnapshotRepository;
import org.candlepin.subscriptions.db.TallyStateRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AccountResetService {

  private static final String BILLABLE_USAGE_AGGREGATE_SUPPRESS_STORE_TOPIC =
      "platform.rhsm-subscriptions.swatch-billable-usage-aggregator-billable-usage-suppress-store-changelog";

  private final EventRecordRepository eventRecordRepo;
  private final HostRepository hostRepo;
  private final TallySnapshotRepository tallySnapshotRepository;
  private final AccountServiceInventoryRepository accountServiceInventoryRepository;
  private final SubscriptionRepository subscriptionRepository;
  private final BillableUsageRemittanceRepository remittanceRepository;
  private final TallyStateRepository tallyStateRepository;
  private final KafkaAdmin kafkaAdmin;

  @Autowired
  public AccountResetService(
      EventRecordRepository eventRecordRepo,
      HostRepository hostRepo,
      TallySnapshotRepository tallySnapshotRepository,
      AccountServiceInventoryRepository accountServiceInventoryRepository,
      SubscriptionRepository subscriptionRepository,
      BillableUsageRemittanceRepository remittanceRepository,
      TallyStateRepository tallyStateRepository,
      KafkaAdmin kafkaAdmin) {
    this.eventRecordRepo = eventRecordRepo;
    this.hostRepo = hostRepo;
    this.tallySnapshotRepository = tallySnapshotRepository;
    this.accountServiceInventoryRepository = accountServiceInventoryRepository;
    this.subscriptionRepository = subscriptionRepository;
    this.remittanceRepository = remittanceRepository;
    this.tallyStateRepository = tallyStateRepository;
    this.kafkaAdmin = kafkaAdmin;
  }

  @Transactional
  public void deleteDataForOrg(String orgId) {
    accountServiceInventoryRepository.deleteByIdOrgId(orgId);
    hostRepo.deleteByOrgId(orgId);
    eventRecordRepo.deleteByOrgId(orgId);
    tallySnapshotRepository.deleteByOrgId(orgId);
    subscriptionRepository.deleteByOrgId(orgId);
    remittanceRepository.deleteByOrgId(orgId);
    tallyStateRepository.deleteByOrgId(orgId);
    deleteKafkaData();
  }

  private void deleteKafkaData() {
    NewTopic billableUsageSuppressStoreEmpty =
        TopicBuilder.name(BILLABLE_USAGE_AGGREGATE_SUPPRESS_STORE_TOPIC)
            .config("cleanup.policy", "delete")
            .build();
    kafkaAdmin.createOrModifyTopics(billableUsageSuppressStoreEmpty);
    deleteAllMessages(BILLABLE_USAGE_AGGREGATE_SUPPRESS_STORE_TOPIC);
    NewTopic billableUsageSuppressStoreReset =
        TopicBuilder.name(BILLABLE_USAGE_AGGREGATE_SUPPRESS_STORE_TOPIC)
            .config("cleanup.policy", "compact")
            .build();
    kafkaAdmin.createOrModifyTopics(billableUsageSuppressStoreReset);
  }

  void deleteAllMessages(String topic) {
    try (AdminClient adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
      var partitions =
          kafkaAdmin
              .describeTopics(BILLABLE_USAGE_AGGREGATE_SUPPRESS_STORE_TOPIC)
              .get(BILLABLE_USAGE_AGGREGATE_SUPPRESS_STORE_TOPIC)
              .partitions();
      Map<TopicPartition, RecordsToDelete> recordsToDelete =
          partitions.stream()
              .collect(
                  toMap(
                      partition -> new TopicPartition(topic, partition.partition()),
                      partition -> RecordsToDelete.beforeOffset(-1)));
      adminClient.deleteRecords(recordsToDelete).all().get();
      log.info("Deleted records in topics: {}", topic);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (Exception e) {
      log.error("Error during deleting topics.", e);
    }
  }
}

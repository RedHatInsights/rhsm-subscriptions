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
package com.redhat.swatch.contract.service;

import com.redhat.swatch.contract.config.Channels;
import com.redhat.swatch.contract.model.TallySnapshot;
import com.redhat.swatch.contract.model.TallySummary;
import com.redhat.swatch.contract.test.resources.InMemoryMessageBrokerKafkaResource;
import com.redhat.swatch.contract.test.resources.LoggerCaptor;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySource;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.jboss.logmanager.LogContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(InMemoryMessageBrokerKafkaResource.class)
class TallySnapshotSummaryConsumerTest {

  private static final String ORG_ID = "org123";
  private static final LoggerCaptor LOGGER_CAPTOR = new LoggerCaptor();

  @Inject @Any InMemoryConnector connector;
  private InMemorySource<List<TallySummary>> tallyInChannel;

  @BeforeAll
  static void configureLogging() {
    LogContext.getLogContext()
        .getLogger(TallySnapshotSummaryConsumer.class.getName())
        .addHandler(LOGGER_CAPTOR);
  }

  @Transactional
  @BeforeEach
  public void setup() {
    tallyInChannel = connector.source(Channels.TALLY_IN);
  }

  @Test
  void testProcessTallySummary() {
    TallySummary tallySummary = givenTallySummaryMessage();

    whenReceiveTallySummary(tallySummary);

    thenLogWithMessage("Processing batch of 1 tally messages");
  }

  private TallySummary givenTallySummaryMessage() {
    TallySnapshot snapshot =
        new TallySnapshot()
            .withId(UUID.randomUUID())
            .withProductId("RHEL")
            .withSnapshotDate(OffsetDateTime.now())
            .withSla(TallySnapshot.Sla.PREMIUM)
            .withUsage(TallySnapshot.Usage.PRODUCTION)
            .withBillingProvider(TallySnapshot.BillingProvider.RED_HAT)
            .withGranularity(TallySnapshot.Granularity.HOURLY);

    return new TallySummary().withOrgId(ORG_ID).withTallySnapshots(List.of(snapshot));
  }

  private void whenReceiveTallySummary(TallySummary... tallySummaries) {
    tallyInChannel.send(List.of(tallySummaries));
  }

  private void thenLogWithMessage(String str) {
    LOGGER_CAPTOR.thenInfoLogWithMessage(str);
  }
}

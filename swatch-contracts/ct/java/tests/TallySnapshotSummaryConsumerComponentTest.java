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
package tests;

import com.redhat.swatch.component.tests.utils.Topics;
import com.redhat.swatch.contract.model.TallySnapshot;
import com.redhat.swatch.contract.model.TallySummary;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class TallySnapshotSummaryConsumerComponentTest extends BaseContractComponentTest {

  private static final String ORG_ID = "org123";
  private static final String PRODUCT_ID = "rosa";

  @Test
  void whenSendMessageThenServiceConsumes() {
    var message = givenTallySummaryMessage();
    whenTallySummaryMessageIsSent(message);
    thenTallySummaryMessageIsConsumed();
  }

  private TallySummary givenTallySummaryMessage() {
    TallySnapshot snapshot =
        new TallySnapshot()
            .withId(UUID.randomUUID())
            .withProductId(PRODUCT_ID)
            .withSnapshotDate(OffsetDateTime.now())
            .withSla(TallySnapshot.Sla.PREMIUM)
            .withUsage(TallySnapshot.Usage.PRODUCTION)
            .withBillingProvider(TallySnapshot.BillingProvider.RED_HAT)
            .withGranularity(TallySnapshot.Granularity.HOURLY);

    return new TallySummary().withOrgId(ORG_ID).withTallySnapshots(List.of(snapshot));
  }

  private void whenTallySummaryMessageIsSent(TallySummary message) {
    kafkaBridge.produceKafkaMessage(Topics.TALLY, message);
  }

  private void thenTallySummaryMessageIsConsumed() {
    service.logs().assertContains("Processing batch of 1 tally messages");
  }
}

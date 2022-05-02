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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import org.candlepin.subscriptions.json.TallySnapshot;
import org.candlepin.subscriptions.json.TallySummary;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles({"worker", "kafka-queue", "test"})
class SnapshotProducerJsonSerializationTest {
  @Autowired KafkaTemplate<String, TallySummary> kafkaTemplate;

  @Test
  void testSerializesDatesToIso8601Format() {
    assertNotNull(kafkaTemplate.getProducerFactory().getValueSerializer());
    byte[] serialized =
        kafkaTemplate
            .getProducerFactory()
            .getValueSerializer()
            .serialize(
                "foobar",
                new TallySummary()
                    .withTallySnapshots(
                        List.of(
                            new TallySnapshot()
                                .withSnapshotDate(OffsetDateTime.parse("2022-04-29T00:00:00Z")))));
    assertEquals(
        "{\"tally_snapshots\":[{\"snapshot_date\":\"2022-04-29T00:00:00Z\"}]}",
        new String(serialized, StandardCharsets.UTF_8));
  }
}

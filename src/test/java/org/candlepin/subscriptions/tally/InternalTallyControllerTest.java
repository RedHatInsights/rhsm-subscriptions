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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.candlepin.subscriptions.db.TallySnapshotRepository;
import org.candlepin.subscriptions.db.model.TallySnapshot;
import org.candlepin.subscriptions.registry.TagProfile;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InternalTallyControllerTest {

  @Mock private SnapshotSummaryProducer summaryProducer;
  @Mock private SnapshotSummaryProducer paygSummaryProducer;
  @Mock private TallySnapshotRepository repository;
  @Mock private ApplicationClock clock;
  @Mock private TagProfile tagProfile;

  @Test
  void resendTallySnapshotsTest() {
    var ids =
        List.of(
            "6bf2f254-0000-0000-0000-54e1ada886c3",
            "deadbeef-0000-0000-0000-defaceace111",
            "cafeface-0000-0000-0000-000000000000");
    var t1 = new TallySnapshot();
    t1.setAccountNumber("1");
    var t2 = new TallySnapshot();
    t2.setAccountNumber("1");
    var t3 = new TallySnapshot();
    t3.setAccountNumber("1");
    var tallyList = List.of(t1, t2, t3);

    when(repository.findAllById(Mockito.anyIterable())).thenReturn(tallyList);

    var controller =
        new InternalTallyController(
            summaryProducer, paygSummaryProducer, repository, tagProfile, clock);
    int count = controller.resendTallySnapshots(ids);
    assertEquals(3, count);

    var summaryMap = Map.of("1", tallyList);
    verify(summaryProducer, times(1)).produceTallySummaryMessages(summaryMap);
  }

  @Test
  void emitPaygRollupsTest() {
    var testDate = LocalDate.of(2022, 5, 1);
    var start = clock.startOfDayUTC(testDate);
    var end = clock.endOfDayUTC(testDate);

    Set<String> paygProducts = Set.of("a", "b");
    when(tagProfile.getTagsForBillingModel("PAYG")).thenReturn(paygProducts);

    var t1 = new TallySnapshot();
    t1.setAccountNumber("1");
    var t2 = new TallySnapshot();
    t2.setAccountNumber("1");
    var t3 = new TallySnapshot();
    t3.setAccountNumber("2");
    when(repository.findByProductIdInAndSnapshotDateBetween(paygProducts, start, end))
        .thenReturn(Stream.of(t1, t2, t3));

    var controller =
        new InternalTallyController(
            summaryProducer, paygSummaryProducer, repository, tagProfile, clock);
    assertEquals(3, controller.emitPaygRollups(testDate));

    var summaryMap = Map.of("1", List.of(t1, t2), "2", List.of(t3));
    verify(paygSummaryProducer, times(1)).produceTallySummaryMessages(summaryMap);
  }
}

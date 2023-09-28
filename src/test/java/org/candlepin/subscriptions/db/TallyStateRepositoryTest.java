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
package org.candlepin.subscriptions.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.candlepin.clock.ApplicationClock;
import org.candlepin.subscriptions.db.model.TallyState;
import org.candlepin.subscriptions.db.model.TallyStateKey;
import org.candlepin.subscriptions.test.TestClockConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureTestDatabase
@SpringBootTest
@ActiveProfiles("test")
@Import(TestClockConfiguration.class)
class TallyStateRepositoryTest {

  @Autowired TallyStateRepository repo;

  @Autowired ApplicationClock clock;

  @Test
  @Transactional
  void saveAndUpdate() {
    String orgId = "test1_org";
    String serviceType = "test1_service_type";
    OffsetDateTime date = clock.now();
    TallyState initialState = new TallyState(orgId, serviceType, date);
    repo.save(initialState);

    Optional<TallyState> state = repo.findById(new TallyStateKey(orgId, serviceType));
    assertTrue(state.isPresent());

    TallyState stateToUpdate = state.get();
    assertEquals(initialState, stateToUpdate);

    stateToUpdate.setLatestEventRecordDate(clock.now().plusHours(1));
    repo.save(stateToUpdate);

    state = repo.findById(new TallyStateKey(orgId, serviceType));
    assertTrue(state.isPresent());
    assertEquals(stateToUpdate, state.get());
  }

  @Test
  @Transactional
  void testFindById() {
    assertTrue(repo.findById(new TallyStateKey("org", "type")).isEmpty());

    String orgId = "test2_org";
    String serviceType = "test2_service_type";
    OffsetDateTime date = clock.now();
    TallyState state = new TallyState(orgId, serviceType, date);
    repo.save(state);

    Optional<TallyState> stateOptional = repo.findById(new TallyStateKey(orgId, serviceType));
    assertTrue(stateOptional.isPresent());
    assertEquals(state, stateOptional.get());
  }
}

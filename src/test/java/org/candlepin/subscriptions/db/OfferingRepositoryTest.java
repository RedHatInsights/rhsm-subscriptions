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

import jakarta.transaction.Transactional;
import java.util.Set;
import org.candlepin.subscriptions.db.model.Offering;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Usage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class OfferingRepositoryTest {

  @Autowired OfferingRepository repository;

  @Test
  @Transactional
  void canPersistAndRetrieveThenRemove() {
    long initialOfferingCount = repository.count();
    final Offering offering = new Offering();
    offering.setSku("testsku");
    offering.setChildSkus(Set.of("childsku1", "childsku2"));
    offering.setProductIds(Set.of(1, 2));
    offering.setUsage(Usage.DEVELOPMENT_TEST);
    offering.setServiceLevel(ServiceLevel.PREMIUM);
    offering.setRole("test");
    offering.setCores(1);
    offering.setSockets(1);
    offering.setProductFamily("test");
    offering.setProductName("test");
    offering.setDescription("test sku");
    offering.setMetered(false);
    repository.save(offering);
    final Offering actual = repository.getOne("testsku");
    assertEquals(offering, actual);
    assertEquals(offering.hashCode(), actual.hashCode());
    repository.delete(actual);
    assertEquals(initialOfferingCount, repository.count());
  }
}

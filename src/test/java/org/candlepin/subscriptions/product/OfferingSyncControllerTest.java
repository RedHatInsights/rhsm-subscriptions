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
package org.candlepin.subscriptions.product;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Optional;
import org.candlepin.subscriptions.db.OfferingRepository;
import org.candlepin.subscriptions.db.model.Offering;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles({"worker", "test"})
class OfferingSyncControllerTest {

  @MockBean OfferingRepository repo;
  @Autowired OfferingSyncController subject;

  @Test
  void testSyncOfferingNew() {
    // Given an Offering that is not yet persisted,
    when(repo.findById(anyString())).thenReturn(Optional.empty());

    Offering sku = new Offering();
    sku.setSku("RH00003");
    sku.setProductIds(Arrays.asList(68, 69, 70, 71, 72));

    // When syncing the Offering,
    subject.syncOffering(sku);

    // Then the Offering should be persisted.
    verify(repo).save(sku);
  }

  @Test
  void testSyncOfferingChanged() {
    // Given an Offering that is different from what is persisted,
    Offering persisted = new Offering();
    persisted.setSku("RH00003");
    persisted.setProductIds(Arrays.asList(68));
    when(repo.findById(anyString())).thenReturn(Optional.of(persisted));

    Offering sku = new Offering();
    sku.setSku("RH00003");
    sku.setProductIds(Arrays.asList(68, 69, 70, 71, 72));

    // When syncing the Offering,
    subject.syncOffering(sku);

    // Then the updated Offering should be persisted.
    verify(repo).save(sku);
  }

  @Test
  void testSyncOfferingUnchanged() {
    // Given an Offering that is equal to what is persisted,
    Offering persisted = new Offering();
    persisted.setSku("RH00003");
    persisted.setProductIds(Arrays.asList(68, 69, 70, 71, 72));
    when(repo.findById(anyString())).thenReturn(Optional.of(persisted));

    Offering sku = new Offering();
    sku.setSku("RH00003");
    sku.setProductIds(Arrays.asList(68, 69, 70, 71, 72));

    // When syncing the Offering,
    subject.syncOffering(sku);

    // Then no persisting should happen.
    verify(repo, never()).save(sku);
  }

  @Test
  void testSyncOfferingNoProductIdsShouldPersist() {
    // Given an Offering that has no engineering product ids,
    Offering sku = new Offering();
    sku.setSku("MW01484"); // This is an actual Offering that has no engineering product ids

    // When syncing the Offering,
    subject.syncOffering(sku);

    // Then it should still persist, since there are Offerings that we need that have no eng prods.
    verify(repo).save(sku);
  }
}

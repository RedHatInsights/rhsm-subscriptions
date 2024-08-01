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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.redhat.swatch.contract.exception.ServiceException;
import com.redhat.swatch.contract.openapi.model.OfferingProductTags;
import com.redhat.swatch.contract.repository.OfferingEntity;
import com.redhat.swatch.contract.repository.OfferingRepository;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.Set;
import org.junit.jupiter.api.Test;

@QuarkusTest
class OfferingProductTagLookupServiceTest {

  @InjectMock OfferingRepository offeringRepository;
  @Inject OfferingProductTagLookupService offeringProductTagLookupService;

  @Test
  void findProductTagsBySku_WhenSkuNotPresent() {
    when(offeringRepository.findById("sku")).thenReturn(null);
    RuntimeException e =
        assertThrows(
            ServiceException.class,
            () -> offeringProductTagLookupService.findPersistedProductTagsBySku("sku"));
    assertEquals("Sku sku not found in Offering", e.getMessage());

    when(offeringRepository.findById("sku")).thenReturn(new OfferingEntity());
    OfferingProductTags productTags2 =
        offeringProductTagLookupService.findPersistedProductTagsBySku("sku");
    assertTrue(productTags2.getData().isEmpty());
  }

  @Test
  void
      findProductTagsBySku_WhenSkuPresentWithNoRoleOrEngIDsThenItShouldNotUseProductNameWhenMeteredFlagIsFalse() {
    OfferingEntity offering = new OfferingEntity();
    offering.setProductName("OpenShift Online");
    offering.setRole(null);
    offering.setProductIds(null);
    offering.setMetered(false);
    when(offeringRepository.findById("sku")).thenReturn(offering);

    OfferingProductTags productTags =
        offeringProductTagLookupService.findPersistedProductTagsBySku("sku");
    assertTrue(productTags.getData().isEmpty());
  }

  @Test
  void
      findProductTagsBySku_WhenSkuPresentWithNoRoleOrEngIDsThenItShouldUseProductNameWhenMeteredFlagIsTrue() {
    OfferingEntity offering = new OfferingEntity();
    offering.setProductName("OpenShift Online");
    offering.setRole(null);
    offering.setProductIds(null);
    offering.setMetered(true);
    when(offeringRepository.findById("sku")).thenReturn(offering);

    OfferingProductTags productTags =
        offeringProductTagLookupService.findPersistedProductTagsBySku("sku");
    assertEquals(1, productTags.getData().size());
    assertEquals("rosa", productTags.getData().get(0));
  }

  @Test
  void findProductTagsBySku_WhenEngIdPresent() {
    OfferingEntity offering = new OfferingEntity();
    offering.setProductIds(Set.of(290));
    when(offeringRepository.findById("sku")).thenReturn(offering);

    OfferingProductTags productTags =
        offeringProductTagLookupService.findPersistedProductTagsBySku("sku");
    assertEquals(1, productTags.getData().size());
    assertEquals("OpenShift Container Platform", productTags.getData().get(0));
  }
}

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
import static org.junit.jupiter.api.Assertions.assertNull;
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
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

@QuarkusTest
class OfferingProductTagLookupServiceTest {

  private static final int RHEL_PRODUCT = 69;
  private static final int RHEL_EUS_PRODUCT = 70;
  private static final int OCP_PRODUCT = 290;
  private static final int NON_EXISTING_PRODUCT = 999;

  @InjectMock OfferingRepository offeringRepository;
  @Inject OfferingProductTagLookupService offeringProductTagLookupService;

  @Test
  void findProductTagsBySkuWhenSkuNotPresent() {
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
      findProductTagsBySkuWhenSkuPresentWithNoRoleOrEngIDsThenItShouldNotUseLevelWhenMeteredFlagIsFalse() {
    OfferingEntity offering = new OfferingEntity();
    offering.setLevel1("OpenShift");
    offering.setLevel2("ROSA - RH OpenShift on AWS");
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
      findProductTagsBySkuWhenSkuPresentWithNoRoleOrEngIDsThenItShouldUseLevelWhenMeteredFlagIsTrue() {
    OfferingEntity offering = new OfferingEntity();
    offering.setLevel1("OpenShift");
    offering.setLevel2("ROSA - RH OpenShift on AWS");
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
  void findProductTagsBySkuWhenEngIdPresent() {
    OfferingEntity offering = new OfferingEntity();
    offering.setProductIds(Set.of(290));
    when(offeringRepository.findById("sku")).thenReturn(offering);

    OfferingProductTags productTags =
        offeringProductTagLookupService.findPersistedProductTagsBySku("sku");
    assertEquals(1, productTags.getData().size());
    assertEquals("OpenShift Container Platform", productTags.getData().get(0));
  }

  @Test
  void discoverProductTagsBySkuShouldPruneIncludedProducts() {
    // given an offering with eng ids that match to the following products
    OfferingEntity offering =
        OfferingEntity.builder()
            .productIds(Set.of(RHEL_PRODUCT, RHEL_EUS_PRODUCT, OCP_PRODUCT))
            .build();
    // discover product tags by configuration
    OfferingProductTags productTags =
        offeringProductTagLookupService.discoverProductTagsBySku(Optional.of(offering));
    // then only the OCP product should be taken as it's configured with the includedSubscriptions
    // property that excludes the other products.
    assertEquals(1, productTags.getData().size());
    assertEquals("OpenShift Container Platform", productTags.getData().get(0));
  }

  @Test
  void discoverProductTagsBySkuShouldDoNothingWhenNoFoundProductTags() {
    // given an offering with eng ids that do not match with any product
    OfferingEntity offering =
        OfferingEntity.builder().productIds(Set.of(NON_EXISTING_PRODUCT)).build();
    // discover product tags by configuration
    OfferingProductTags productTags =
        offeringProductTagLookupService.discoverProductTagsBySku(Optional.of(offering));
    // then product tags should be null
    assertNull(productTags.getData());
  }
}

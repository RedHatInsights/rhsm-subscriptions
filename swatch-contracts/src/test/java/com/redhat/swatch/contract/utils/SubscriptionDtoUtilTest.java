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
package com.redhat.swatch.contract.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.redhat.swatch.clients.subscription.api.model.ExternalReference;
import com.redhat.swatch.clients.subscription.api.model.Subscription;
import com.redhat.swatch.clients.subscription.api.model.SubscriptionProduct;
import com.redhat.swatch.contract.repository.BillingProvider;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class SubscriptionDtoUtilTest {
  @Test
  void testExtractSku() {
    var dto = new Subscription();
    SubscriptionProduct product =
        new SubscriptionProduct().parentSubscriptionProductId(null).sku("testSku");
    SubscriptionProduct childSku =
        new SubscriptionProduct().parentSubscriptionProductId(123).sku("childSku");
    List<SubscriptionProduct> products = Arrays.asList(product, childSku);
    dto.setSubscriptionProducts(products);

    assertEquals("testSku", SubscriptionDtoUtil.extractSku(dto));
  }

  @Test
  void testExtractSkuFailsWithImproperSubscription() {
    var dto = new Subscription();
    SubscriptionProduct product =
        new SubscriptionProduct().parentSubscriptionProductId(null).sku("testSku");
    SubscriptionProduct childSku =
        new SubscriptionProduct().parentSubscriptionProductId(null).sku("childSku");
    List<SubscriptionProduct> products = Arrays.asList(product, childSku);
    dto.setSubscriptionProducts(products);

    assertThrows(IllegalStateException.class, () -> SubscriptionDtoUtil.extractSku(dto));
  }

  @Test
  void testExtractBillingProviderIdRHMarketplace() {
    var dto = new Subscription();

    ExternalReference extRef = new ExternalReference();
    extRef.setSubscriptionID("test123");
    dto.putExternalReferencesItem("ibmmarketplace", extRef);

    assertEquals("test123", SubscriptionDtoUtil.extractBillingProviderId(dto));
  }

  @Test
  void testExtractBillingProviderIdAWSMarketplace() {
    var dto = new Subscription();

    ExternalReference extRef = new ExternalReference();
    extRef.setProductCode("testProduct456");
    extRef.setCustomerID("testCustomer123");
    extRef.setSellerAccount("testSellerAccount789");
    dto.putExternalReferencesItem(SubscriptionDtoUtil.AWS_MARKETPLACE, extRef);

    assertEquals(
        "testProduct456;testCustomer123;testSellerAccount789",
        SubscriptionDtoUtil.extractBillingProviderId(dto));
  }

  @Test
  void testExtractBillingProviderRHMarketplace() {
    var dto = new Subscription();

    dto.putExternalReferencesItem("ibmmarketplace", new ExternalReference());

    assertEquals(BillingProvider.RED_HAT, SubscriptionDtoUtil.populateBillingProvider(dto));
  }

  @Test
  void testExtractBillingProviderAWSMarketplace() {
    var dto = new Subscription();

    dto.putExternalReferencesItem(SubscriptionDtoUtil.AWS_MARKETPLACE, new ExternalReference());

    assertEquals(BillingProvider.AWS, SubscriptionDtoUtil.populateBillingProvider(dto));
  }

  @Test
  void testExtractBillingAccountIdExternalReference() {
    var dto = new Subscription();

    ExternalReference extRef = new ExternalReference();
    extRef.setCustomerAccountID("123456789123");
    dto.putExternalReferencesItem(SubscriptionDtoUtil.AWS_MARKETPLACE, extRef);
    assertEquals("123456789123", SubscriptionDtoUtil.extractBillingAccountId(dto));
  }

  @Test
  void testExtractBillingAccountNoAWSExternalReference() {
    var dto = new Subscription();

    dto.putExternalReferencesItem("ibmmarketplace", new ExternalReference());

    assertEquals(null, SubscriptionDtoUtil.extractBillingAccountId(dto));
  }

  @Test
  void testExtractBillingAccountNoExternalReferences() {
    var dto = new Subscription();

    assertEquals(null, SubscriptionDtoUtil.extractBillingAccountId(dto));
  }
}

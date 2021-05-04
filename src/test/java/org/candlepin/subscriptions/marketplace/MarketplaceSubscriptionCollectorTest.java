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
package org.candlepin.subscriptions.marketplace;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.ParameterizedTest.*;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.candlepin.subscriptions.subscription.SubscriptionService;
import org.candlepin.subscriptions.subscription.api.model.ExternalReference;
import org.candlepin.subscriptions.subscription.api.model.Subscription;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
class MarketplaceSubscriptionCollectorTest {

  private static final Subscription SUB_WITH_IBMMARKETPLACE =
      new Subscription()
          .externalReferences(
              Map.of(
                  "ibmmarketplace",
                  new ExternalReference()
                      .subscriptionID("GGJe4KCgzUBf73YC5rjJvDkM")
                      .accountID("account-ID-04072021-1841")));

  @Mock SubscriptionService subscriptionService;
  @InjectMocks MarketplaceSubscriptionCollector marketplaceSubscriptionCollector;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  void testRequestSubscriptions() {

    when(subscriptionService.getSubscriptionsByAccountNumber(anyString()))
        .thenReturn(List.of(SUB_WITH_IBMMARKETPLACE));

    assertEquals(
        List.of(SUB_WITH_IBMMARKETPLACE),
        marketplaceSubscriptionCollector.requestSubscriptions("account123"));
  }

  @ParameterizedTest(name = DISPLAY_NAME_PLACEHOLDER + " " + DEFAULT_DISPLAY_NAME)
  @MethodSource("generateMockSubscriptionPayloads")
  void testFilterNonApplicableSubscriptions(List<Subscription> input, List<Subscription> expected) {

    assertEquals(
        expected, marketplaceSubscriptionCollector.filterNonApplicableSubscriptions(input));
  }

  static Stream<Arguments> generateMockSubscriptionPayloads() {
    // 1) getExternalReferences null
    var subscription = new Subscription().externalReferences(null);
    Arguments nullExternalReference = Arguments.of(List.of(subscription), Collections.emptyList());

    // 2) External getExternalReferences empty
    var subscription1 = new Subscription().externalReferences(Collections.emptyMap());
    Arguments emptyExternalReferences =
        Arguments.of(List.of(subscription1), Collections.emptyList());

    // 3) getExternalReferences doesn't contain "ibmmarketplace"
    var subscription2 =
        new Subscription().externalReferences(Map.of("bananas", new ExternalReference()));

    Arguments nonIbmMarketplaceExternalReferences =
        Arguments.of(List.of(subscription2), Collections.emptyList());

    // 4) contain only "ibmmarketplace"
    var subscription3 = SUB_WITH_IBMMARKETPLACE;

    Arguments ibmMarketplaceExternalReferences =
        Arguments.of(List.of(subscription3), List.of(subscription3));

    // 5) contains "ibmmarketplace" plus another

    var subscription4 =
        new Subscription()
            .externalReferences(
                Map.of(
                    "ibmmarketplace",
                    new ExternalReference()
                        .subscriptionID("GGJe4KCgzUBf73YC5rjJvDkM")
                        .accountID("account-ID-04072021-1841"),
                    "bananas",
                    new ExternalReference()));

    Arguments ibmMarketplacePlusNonIbmMarketplaceExternalReferences =
        Arguments.of(List.of(subscription4), List.of(subscription4));

    return Stream.of(
        nullExternalReference,
        emptyExternalReferences,
        nonIbmMarketplaceExternalReferences,
        ibmMarketplaceExternalReferences,
        ibmMarketplacePlusNonIbmMarketplaceExternalReferences);
  }
}

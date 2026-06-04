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
package com.redhat.swatch.contract.product.umb;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import java.io.IOException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class SubscriptionMessageTests {
  @Test
  void testCanDeserializeSampleMessage() throws IOException {
    XmlMapper mapper = CanonicalMessage.createMapper();
    CanonicalMessage actual =
        mapper.readValue(
            getClass().getClassLoader().getResource("mocked-subscription-message.xml"),
            CanonicalMessage.class);
    CanonicalMessage expected =
        CanonicalMessage.builder()
            .payload(
                Payload.builder()
                    .sync(
                        Sync.builder()
                            .subscription(
                                UmbSubscription.builder()
                                    .identifiers(
                                        Identifiers.builder()
                                            .references(
                                                new Reference[] {
                                                  Reference.builder()
                                                      .system("EBS")
                                                      .entityName("Account")
                                                      .qualifier("number")
                                                      .value("account123")
                                                      .build(),
                                                  Reference.builder()
                                                      .system("WEB")
                                                      .entityName("Customer")
                                                      .qualifier("id")
                                                      .value("org123_ICUST")
                                                      .build(),
                                                })
                                            .ids(
                                                new Reference[] {
                                                  Reference.builder()
                                                      .system("SUBSCRIPTION")
                                                      .entityName("Subscription")
                                                      .qualifier("number")
                                                      .value("1234")
                                                      .build()
                                                })
                                            .build())
                                    .status(
                                        SubscriptionStatus.builder()
                                            .startDate(
                                                LocalDateTime.parse("2020-01-01T12:34:56.789"))
                                            .state("Active")
                                            .build())
                                    .products(
                                        new SubscriptionProduct[] {
                                          SubscriptionProduct.builder()
                                              .sku("MW01882")
                                              .status(
                                                  new SubscriptionProductStatus[] {
                                                    SubscriptionProductStatus.builder()
                                                        .state("Active")
                                                        .startDate(
                                                            LocalDateTime.parse(
                                                                "2020-01-01T12:34:56.789"))
                                                        .build()
                                                  })
                                              .product(
                                                  SubscriptionProduct.builder()
                                                      .sku("SVC2681")
                                                      .status(
                                                          new SubscriptionProductStatus[] {
                                                            SubscriptionProductStatus.builder()
                                                                .state("Active")
                                                                .startDate(
                                                                    LocalDateTime.parse(
                                                                        "2023-04-24T00:00:00.000"))
                                                                .build(),
                                                            SubscriptionProductStatus.builder()
                                                                .state("Signed")
                                                                .startDate(
                                                                    LocalDateTime.parse(
                                                                        "2023-04-24T00:00:00.000"))
                                                                .build(),
                                                            SubscriptionProductStatus.builder()
                                                                .state("Terminated")
                                                                .startDate(
                                                                    LocalDateTime.parse(
                                                                        "2023-04-24T14:17:02.000"))
                                                                .build()
                                                          })
                                                      .build())
                                              .build()
                                        })
                                    .effectiveStartDate(
                                        LocalDateTime.parse("2020-01-01T00:00:00.000"))
                                    .effectiveEndDate(
                                        LocalDateTime.parse("2030-01-01T00:00:00.000"))
                                    .quantity(1)
                                    .build())
                            .build())
                    .build())
            .build();
    assertEquals(expected, actual);
    actual.getPayload().getSync().getSubscription().getEffectiveStartDateInUtc();
  }
}

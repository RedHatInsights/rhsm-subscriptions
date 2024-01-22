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
package org.candlepin.subscriptions.umb;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import java.io.IOException;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class OperationalProductMessageTest {
  @Test
  void testCanDeserializeSampleSvcMessage() throws IOException {
    XmlMapper mapper = CanonicalMessage.createMapper();
    CanonicalMessage actual =
        mapper.readValue(
            getClass().getClassLoader().getResource("mocked-svc-product-message.xml"),
            CanonicalMessage.class);
    CanonicalMessage expected =
        CanonicalMessage.builder()
            .payload(
                Payload.builder()
                    .sync(
                        Sync.builder()
                            .operationalProduct(
                                UmbOperationalProduct.builder()
                                    .productRelationship(
                                        ProductRelationship.builder()
                                            .parentProduct(
                                                ParentProduct.builder().sku("RH0180191").build())
                                            .build())
                                    .identifiers(
                                        Identifiers.builder()
                                            .references(
                                                Stream.of(
                                                        273, 274, 272, 182, 127, 240, 246, 91, 133,
                                                        180, 84, 70, 86, 94, 93, 176, 92, 317, 318,
                                                        271, 69, 201, 205, 394, 395, 408, 491, 479,
                                                        588, 605)
                                                    .map(
                                                        id ->
                                                            Reference.builder()
                                                                .system("PRODUCT")
                                                                .entityName("Engineering Product")
                                                                .qualifier("number")
                                                                .value(id.toString())
                                                                .build())
                                                    .collect(Collectors.toList())
                                                    .toArray(new Reference[] {}))
                                            .build())
                                    .attributes(
                                        new ProductAttribute[] {
                                          ProductAttribute.builder()
                                              .code("CORES")
                                              .name("Cores")
                                              .value("0")
                                              .build(),
                                          ProductAttribute.builder()
                                              .code("SOCKET_LIMIT")
                                              .name("Socket Limit")
                                              .value("0")
                                              .build()
                                        })
                                    .sku("SVCRH01")
                                    .skuDescription("Red Hat Enterprise Linux Server")
                                    .build())
                            .build())
                    .build())
            .build();
    assertEquals(expected, actual);
  }

  @Test
  void testCanDeserializeSampleMessage() throws IOException {
    XmlMapper mapper = CanonicalMessage.createMapper();
    CanonicalMessage actual =
        mapper.readValue(
            getClass().getClassLoader().getResource("mocked-product-message.xml"),
            CanonicalMessage.class);
    CanonicalMessage expected =
        CanonicalMessage.builder()
            .payload(
                Payload.builder()
                    .sync(
                        Sync.builder()
                            .operationalProduct(
                                UmbOperationalProduct.builder()
                                    .productRelationship(
                                        ProductRelationship.builder()
                                            .childProducts(
                                                new ChildProduct[] {
                                                  ChildProduct.builder().sku("SVCRH01").build(),
                                                  ChildProduct.builder().sku("SVCRH01V4").build(),
                                                  ChildProduct.builder().sku("SVCMPV4").build()
                                                })
                                            .parentProduct(
                                                ParentProduct.builder().sku("RH0180191").build())
                                            .build())
                                    .attributes(
                                        new ProductAttribute[] {
                                          ProductAttribute.builder()
                                              .code("PRODUCT_NAME")
                                              .name("Product Name")
                                              .value("RHEL Server")
                                              .build(),
                                          ProductAttribute.builder()
                                              .code("PRODUCT_FAMILY")
                                              .name("Product Family")
                                              .value("Red Hat Enterprise Linux")
                                              .build(),
                                          ProductAttribute.builder()
                                              .code("ENTITLEMENT_QTY")
                                              .name("Entitlement Qty")
                                              .value("1")
                                              .build(),
                                          ProductAttribute.builder()
                                              .code("SERVICE_TYPE")
                                              .name("Service Type")
                                              .value("Standard")
                                              .build(),
                                          ProductAttribute.builder()
                                              .code("SOCKET_LIMIT")
                                              .name("Socket Limit")
                                              .value("2")
                                              .build(),
                                          ProductAttribute.builder()
                                              .code("USAGE")
                                              .name("Usage")
                                              .value("Production")
                                              .build(),
                                          ProductAttribute.builder()
                                              .code("METERED")
                                              .name("Metered")
                                              .value("Y")
                                              .build()
                                        })
                                    .sku("RH0180191")
                                    .skuDescription(
                                        "Red Hat Enterprise Linux Server, Standard (1-2 sockets) (Up to 4 guests) with Smart Management")
                                    .role("Red Hat Enterprise Linux Server")
                                    .build())
                            .build())
                    .build())
            .build();
    assertEquals(expected, actual);
  }
}

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
package domain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

/** Test data model for building offering test scenarios in component tests. */
@Getter
@SuperBuilder
@AllArgsConstructor
public class Offering {

  // Constants for RHEL for x86 offering defaults
  private static final String RHEL_DESCRIPTION = "Test component for RHEL for x86";
  private static final String RHEL_ROLE = "Red Hat Enterprise Linux Server";

  // Constants for ROSA offering defaults
  private static final String ROSA_DESCRIPTION = "Test component for ROSA";
  private static final String ROSA_LEVEL1 = "OpenShift";
  private static final String ROSA_LEVEL2 = "ROSA - RH OpenShift on AWS";

  // Common Constants
  private static final String METERED_YES = "Y";
  private static final String METERED_NO = "N";

  private final String sku;
  private final String description;
  private final Integer cores;
  private final Integer sockets;
  private final String level1;
  private final String level2;
  private final String metered;
  private final ServiceLevel serviceLevel;
  private final Usage usage;
  private final String role;
  private final List<Integer> engProducts;

  public static Offering buildRhelOffering(String sku, Double cores, Double sockets) {
    Objects.requireNonNull(sku, "sku cannot be null");

    return Offering.builder()
        .sku(sku)
        .description(RHEL_DESCRIPTION)
        .metered(METERED_NO)
        .cores(Optional.ofNullable(cores).map(Double::intValue).orElse(null))
        .sockets(Optional.ofNullable(sockets).map(Double::intValue).orElse(null))
        .serviceLevel(ServiceLevel.PREMIUM)
        .usage(Usage.PRODUCTION)
        .engProducts(List.of(69, 479))
        .role(RHEL_ROLE)
        .build();
  }

  public static Offering buildRosaOffering(String sku) {
    Objects.requireNonNull(sku, "sku cannot be null");

    return Offering.builder()
        .sku(sku)
        .description(ROSA_DESCRIPTION)
        .level1(ROSA_LEVEL1)
        .level2(ROSA_LEVEL2)
        .metered(METERED_YES)
        .serviceLevel(ServiceLevel.PREMIUM)
        .usage(Usage.PRODUCTION)
        .engProducts(List.of())
        .build();
  }
}

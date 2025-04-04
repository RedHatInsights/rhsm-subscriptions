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
package com.redhat.swatch.hbi.domain.normalization.facts;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class NormalizedFactsTest {

  @Test
  void testIsGuest_whenVirtualAndHasHypervisorUuid_shouldReturnTrue() {

    NormalizedFacts facts =
        NormalizedFacts.builder().isVirtual(true).hypervisorUuid("valid-hypervisor-uuid").build();

    boolean result = facts.isGuest();

    assertTrue(
        result,
        "Expected isGuest to return true when isVirtual is true and hypervisorUuid is not empty");
  }

  @Test
  void testIsGuest_whenVirtualAndNoHypervisorUuid_shouldReturnFalse() {

    NormalizedFacts facts = NormalizedFacts.builder().isVirtual(true).hypervisorUuid("").build();

    boolean result = facts.isGuest();

    assertFalse(
        result,
        "Expected isGuest to return false when isVirtual is true but hypervisorUuid is empty");
  }

  @Test
  void testIsGuest_whenVirtualAndNullHypervisorUuid_shouldReturnFalse() {

    NormalizedFacts facts = NormalizedFacts.builder().isVirtual(true).hypervisorUuid(null).build();

    boolean result = facts.isGuest();

    assertFalse(
        result,
        "Expected isGuest to return false when isVirtual is true but hypervisorUuid is null");
  }

  @Test
  void testIsGuest_whenNotVirtualAndHasHypervisorUuid_shouldReturnFalse() {

    NormalizedFacts facts =
        NormalizedFacts.builder().isVirtual(false).hypervisorUuid("valid-hypervisor-uuid").build();

    boolean result = facts.isGuest();

    assertFalse(
        result,
        "Expected isGuest to return false when isVirtual is false regardless of hypervisorUuid");
  }

  @Test
  void testIsGuest_whenNotVirtualAndNoHypervisorUuid_shouldReturnFalse() {

    NormalizedFacts facts = NormalizedFacts.builder().isVirtual(false).hypervisorUuid("").build();

    boolean result = facts.isGuest();

    assertFalse(
        result,
        "Expected isGuest to return false when isVirtual is false and hypervisorUuid is empty");
  }
}

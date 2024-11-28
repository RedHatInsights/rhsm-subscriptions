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
package com.redhat.swatch.hbi.events.normalization.facts;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.redhat.swatch.hbi.events.dtos.hbi.HbiHost;
import com.redhat.swatch.hbi.events.dtos.hbi.HbiHostFacts;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HbiFactExtractorTest {

  @Test
  void testFactExtractionWhenNotProvidedFromHbi() {
    HbiHost hbiHost = new HbiHost();
    HbiFactExtractor factExtractor = new HbiFactExtractor(hbiHost);
    assertTrue(factExtractor.getRhsmFacts().isEmpty());
    assertTrue(factExtractor.getSatelliteFacts().isEmpty());
    assertTrue(factExtractor.getQpcFacts().isEmpty());
    // Extractor will always instantiate SystemProfileFacts event if the
    // fact values are null.
    assertNotNull(factExtractor.getSystemProfileFacts());
  }

  @Test
  void testFactExtractionWhenProvidedFromHbi() {
    HbiHost hbiHost = new HbiHost();

    HbiHostFacts rhsmFacts = new HbiHostFacts();
    rhsmFacts.setNamespace(RhsmFacts.RHSM_FACTS_NAMESPACE);
    rhsmFacts.setFacts(Map.of(RhsmFacts.SYSTEM_PURPOSE_UNITS_FACT, "Sockets"));

    HbiHostFacts satelliteFacts = new HbiHostFacts();
    satelliteFacts.setNamespace(SatelliteFacts.SATELLITE_FACTS_NAMESPACE);
    satelliteFacts.getFacts().put(SatelliteFacts.SLA_FACT, "Premium");

    HbiHostFacts qpcFacts = new HbiHostFacts();
    qpcFacts.setNamespace(QpcFacts.QPC_FACTS_NAMESPACE);
    qpcFacts.getFacts().put(QpcFacts.PRODUCT_ID_FACT, List.of("69"));

    hbiHost.setFacts(List.of(rhsmFacts, satelliteFacts, qpcFacts));

    HbiFactExtractor factExtractor = new HbiFactExtractor(hbiHost);
    assertTrue(factExtractor.getRhsmFacts().isPresent());
    assertTrue(factExtractor.getSatelliteFacts().isPresent());
    assertTrue(factExtractor.getQpcFacts().isPresent());
    // Extractor will always instantiate SystemProfileFacts event if the
    // fact values are null.
    assertNotNull(factExtractor.getSystemProfileFacts());
  }
}

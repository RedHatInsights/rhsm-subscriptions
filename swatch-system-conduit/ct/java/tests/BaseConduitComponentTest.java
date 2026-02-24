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
package tests;

import static com.redhat.swatch.component.tests.utils.Topics.CONDUIT_TASKS;
import static com.redhat.swatch.component.tests.utils.Topics.INVENTORY_HOST_INGRESS;

import api.ConduitWiremockService;
import com.redhat.swatch.component.tests.api.ComponentTest;
import com.redhat.swatch.component.tests.api.KafkaBridge;
import com.redhat.swatch.component.tests.api.KafkaBridgeService;
import com.redhat.swatch.component.tests.api.SpringBoot;
import com.redhat.swatch.component.tests.api.SwatchService;
import com.redhat.swatch.component.tests.api.Wiremock;
import com.redhat.swatch.component.tests.utils.RandomUtils;
import org.junit.jupiter.api.BeforeEach;

@ComponentTest(name = "swatch-system-conduit")
public class BaseConduitComponentTest {

  @KafkaBridge
  static KafkaBridgeService kafkaBridge =
      new KafkaBridgeService()
          .subscribeToTopic(CONDUIT_TASKS)
          .subscribeToTopic(INVENTORY_HOST_INGRESS);

  @Wiremock static ConduitWiremockService wiremock = new ConduitWiremockService();

  @SpringBoot(service = "swatch-system-conduit")
  static SwatchService service = new SwatchService();

  protected String orgId;

  @BeforeEach
  void setUp() {
    orgId = RandomUtils.generateRandom();
  }
}

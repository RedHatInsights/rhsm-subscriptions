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
package api;

import static com.redhat.swatch.component.tests.utils.Topics.BILLABLE_USAGE_STATUS;

import api.AzureWiremockService;
import com.redhat.swatch.component.tests.api.KafkaBridge;
import com.redhat.swatch.component.tests.api.KafkaBridgeService;
import com.redhat.swatch.component.tests.api.Quarkus;
import com.redhat.swatch.component.tests.api.SwatchService;
import com.redhat.swatch.component.tests.api.Wiremock;
import com.redhat.swatch.component.tests.core.BaseServiceTest;
import java.util.Set;
import org.junit.jupiter.api.Tag;

@Tag("azure")
public abstract class BaseAzureTest extends BaseServiceTest {

  @Wiremock
  protected static AzureWiremockService wiremock = new AzureWiremockService();

  @KafkaBridge
  protected static KafkaBridgeService kafkaBridge =
      new KafkaBridgeService().subscribeToTopic(BILLABLE_USAGE_STATUS);

  @Quarkus(service = "swatch-producer-azure")
  protected static SwatchService service = new SwatchService();

  @Override
  protected KafkaBridgeService kafkaBridge() {
    return kafkaBridge;
  }

  @Override
  protected Set<String> topicsToEmptyAfterEach() {
    return Set.of(BILLABLE_USAGE_STATUS);
  }
}



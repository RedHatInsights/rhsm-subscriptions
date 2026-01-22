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

import static com.redhat.swatch.component.tests.utils.Topics.TALLY;

import com.redhat.swatch.component.tests.api.ComponentTest;
import com.redhat.swatch.component.tests.api.KafkaBridge;
import com.redhat.swatch.component.tests.api.KafkaBridgeService;
import com.redhat.swatch.component.tests.api.SpringBoot;
import com.redhat.swatch.component.tests.api.SwatchService;
import java.util.UUID;

@ComponentTest(name = "swatch-tally")
public class BaseTallyComponentTest {

  @KafkaBridge
  static KafkaBridgeService kafkaBridge = new KafkaBridgeService().subscribeToTopic(TALLY);

  @SpringBoot(service = "swatch-tally")
  static SwatchService service = new SwatchService();

  /**
   * Trace ID for this test method instance. Generated once per test method and used to track all
   * actions performed by this test. This trace ID is included in all Kafka messages produced by
   * this test via the "traceparent" header in W3C Trace Context format.
   */
  protected final String TRACE_ID = UUID.randomUUID().toString();

  /**
   * Formats the TRACE_ID as a W3C traceparent header value. Format:
   * 00-{trace-id}-{parent-id}-{flags} where trace-id is 32 hex chars, parent-id is 16 hex chars,
   * and flags is 2 hex chars.
   */
  protected String getTraceParentHeader() {
    // Remove dashes from UUID to get 32 hex characters
    String traceIdHex = TRACE_ID.replace("-", "");
    // Generate a random parent ID (16 hex characters = 8 bytes)
    String parentId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    // Flags: 01 = sampled
    return String.format("00-%s-%s-01", traceIdHex, parentId);
  }


  {
    // Instance initializer - runs when each test instance is created
    // Set the traceparent header in KafkaBridgeService so it's automatically included in messages
    String traceParent = getTraceParentHeader();
    kafkaBridge.setTraceParentHeader(traceParent);
    System.out.println("Initialized TRACE_ID for test method: " + TRACE_ID);
    System.out.println("Traceparent header value: " + traceParent);
  }
}

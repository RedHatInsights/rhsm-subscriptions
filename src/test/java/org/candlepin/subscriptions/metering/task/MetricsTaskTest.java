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
package org.candlepin.subscriptions.metering.task;

import static org.mockito.Mockito.*;

import java.time.OffsetDateTime;
import org.candlepin.subscriptions.json.Measurement.Uom;
import org.candlepin.subscriptions.metering.service.prometheus.PrometheusMeteringController;
import org.candlepin.subscriptions.prometheus.ApiException;
import org.candlepin.subscriptions.test.TestClockConfiguration;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MetricsTaskTest {

  @Mock private PrometheusMeteringController controller;

  @Test
  void testExecute() throws ApiException {
    ApplicationClock clock = new TestClockConfiguration().adjustableClock();
    OffsetDateTime expEnd = clock.now();
    OffsetDateTime expStart = expEnd.minusDays(1);
    String expAccount = "test-account";
    String expProductTag = "OpenShift";
    Uom expMetric = Uom.CORES;

    MetricsTask task =
        new MetricsTask(controller, expAccount, expProductTag, expMetric, expStart, expEnd);
    task.execute();
    verify(controller).collectMetrics("OpenShift", Uom.CORES, expAccount, expStart, expEnd);
  }
}

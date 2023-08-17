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
package org.candlepin.subscriptions.conduit.inventory;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.convert.DurationUnit;

/** Sub-class for inventory service properties */
@Getter
@Setter
public class InventoryServiceProperties {
  private boolean useStub;
  private boolean prettyPrintJson;
  private String url;
  private String apiKey;
  private String kafkaHostIngressTopic;
  private int apiHostUpdateBatchSize = 50;
  private boolean tolerateMissingAccountNumber;

  @DurationUnit(ChronoUnit.HOURS)
  private Duration staleHostOffset = Duration.ofHours(0);

  @DurationUnit(ChronoUnit.HOURS)
  private Duration hostLastSyncThreshold = Duration.ofHours(24);
}

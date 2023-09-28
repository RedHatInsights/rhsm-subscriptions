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
package org.candlepin.subscriptions.db.model;

import java.io.Serializable;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Represents the PK to be used for an {@link EventRecord} */
@Getter
@Setter
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class EventRecordId implements Serializable {

  private String orgId;
  private String eventType;
  private String eventSource;
  private String instanceId;
  private OffsetDateTime timestamp;
  private OffsetDateTime recordDate;

  public EventRecordId(
      String orgId,
      String eventType,
      String eventSource,
      String instanceId,
      OffsetDateTime timestamp) {
    // NOTE: recordDate is left out because it is generated on persist.
    this.orgId = orgId;
    this.eventType = eventType;
    this.eventSource = eventSource;
    this.instanceId = instanceId;
    this.timestamp = timestamp;
  }
}

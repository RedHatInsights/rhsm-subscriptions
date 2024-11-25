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
package com.redhat.swatch.hbi.events.repository;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.ZonedDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@EqualsAndHashCode(callSuper = false)
@Entity
@Table(
    name = "hypervisor_relationship",
    uniqueConstraints = {
      @UniqueConstraint(
          columnNames = {"hypervisor_uuid"},
          name = "unique_hypervisor_uuid")
    })
@Data
@NoArgsConstructor
public class HypervisorRelationship extends PanacheEntityBase {

  @EmbeddedId private HypervisorRelationshipId id;

  @Column(name = "hypervisor_uuid", nullable = true)
  private String hypervisorUuid;

  @Column(name = "creation_date", nullable = false)
  private ZonedDateTime creationDate;

  @Column(name = "last_updated", nullable = false)
  private ZonedDateTime lastUpdated;

  @Column(name = "facts", columnDefinition = "jsonb")
  private String facts;

  @Column(name = "measurements", columnDefinition = "jsonb")
  private String measurements;
}

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
package com.redhat.swatch.utilization.data;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import lombok.Getter;
import org.hibernate.annotations.CurrentTimestamp;
import org.hibernate.annotations.SourceType;

/**
 * A MappedSuperclass to hold the information for a last_updated column. This column is used to
 * limit the number of rows fetched when we do data exports to the data warehouse.
 *
 * <p>Hibernate will populate the value for this column using a RETURNING clause which eliminates an
 * extra round trip to the database. See <a
 * href="https://in.relation.to/2024/04/19/generated-values/">here</a>
 */
@Getter
@MappedSuperclass
public abstract class ModificationTrackedEntity extends PanacheEntityBase {

  // Do not include in equals() as this doesn't affect logical equality
  @Column(name = "last_updated")
  @CurrentTimestamp(source = SourceType.DB)
  private Instant lastUpdated = null;
}

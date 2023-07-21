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
package org.candlepin.subscriptions.db.model.config;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.MappedSuperclass;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;

/** Base class for configuration DB objects. */
@MappedSuperclass
public class BaseConfig implements Serializable {
  @Enumerated(EnumType.STRING)
  @Column(name = "opt_in_type")
  protected OptInType optInType;

  @Column(name = "created")
  protected OffsetDateTime created;

  @Column(name = "updated")
  protected OffsetDateTime updated;

  public OptInType getOptInType() {
    return optInType;
  }

  public void setOptInType(OptInType optInType) {
    this.optInType = optInType;
  }

  public OffsetDateTime getCreated() {
    return created;
  }

  public void setCreated(OffsetDateTime created) {
    this.created = created;
  }

  public OffsetDateTime getUpdated() {
    return updated;
  }

  public void setUpdated(OffsetDateTime updated) {
    this.updated = updated;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (!(o instanceof BaseConfig)) {
      return false;
    }

    BaseConfig that = (BaseConfig) o;
    return optInType == that.optInType
        && Objects.equals(created, that.created)
        && Objects.equals(updated, that.updated);
  }

  @Override
  public int hashCode() {
    return Objects.hash(optInType, created, updated);
  }
}

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
package com.redhat.swatch.contract.repository;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Map;
import java.util.Objects;

/**
 * System purpose service level
 *
 * <p>SLA or service level agreement is defined on a given subscription, and can be set as an
 * attribute on a system to associate it with a specific SLA requirement.
 */
public enum ServiceLevel implements StringValueEnum {
  EMPTY(""),
  PREMIUM("Premium"),
  STANDARD("Standard"),
  SELF_SUPPORT("Self-Support"),
  _ANY("_ANY"); // NOSONAR

  private static final Map<String, ServiceLevel> VALUE_ENUM_MAP =
      StringValueEnum.initializeImmutableMap(ServiceLevel.class);

  private final String value;

  ServiceLevel(String value) {
    this.value = value;
  }

  /**
   * Parse the service level from its string representation (excluding special value _ANY).
   *
   * <p>NOTE: this method will not return the special value ANY, and gives UNSPECIFIED for any
   * invalid value.
   *
   * @param value String representation of the SLA, as seen in a host record
   * @return the ServiceLevel enum; UNSPECIFIED if unparseable.
   */
  public static ServiceLevel fromString(String value) {
    return StringValueEnum.getValueOf(ServiceLevel.class, VALUE_ENUM_MAP, value, EMPTY);
  }

  public String getValue() {
    return value;
  }

  /** JPA converter for ServiceLevel */
  @Converter(autoApply = true)
  public static class EnumConverter implements AttributeConverter<ServiceLevel, String> {

    @Override
    public String convertToDatabaseColumn(ServiceLevel attribute) {
      return Objects.nonNull(attribute) ? attribute.getValue() : null;
    }

    @Override
    public ServiceLevel convertToEntityAttribute(String dbData) {
      return ServiceLevel.fromString(dbData);
    }
  }
}

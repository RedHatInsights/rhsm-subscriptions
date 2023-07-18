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

/**
 * System purpose usage
 *
 * <p>Usage represents the class of usage for a given system or subscription.
 */
public enum Usage implements StringValueEnum {
  EMPTY(""),
  PRODUCTION("Production"),
  DEVELOPMENT_TEST("Development/Test"),
  DISASTER_RECOVERY("Disaster Recovery"),
  _ANY("_ANY"); // NOSONAR

  private static final Map<String, Usage> VALUE_ENUM_MAP =
      StringValueEnum.initializeImmutableMap(Usage.class);

  private final String value;

  Usage(String value) {
    this.value = value;
  }

  /**
   * Parse the usage from its string representation
   *
   * @param value String representation of the Usage, as seen in a host record
   * @return the Usage enum
   */
  public static Usage fromString(String value) {
    return StringValueEnum.getValueOf(Usage.class, VALUE_ENUM_MAP, value, EMPTY);
  }

  public String getValue() {
    return value;
  }

  /** JPA converter for Usage */
  @Converter(autoApply = true)
  public static class EnumConverter implements AttributeConverter<Usage, String> {
    @Override
    public String convertToDatabaseColumn(Usage attribute) {
      if (attribute == null) {
        return null;
      }
      return attribute.getValue();
    }

    @Override
    public Usage convertToEntityAttribute(String dbData) {
      return Usage.fromString(dbData);
    }
  }
}

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
package com.redhat.swatch.billable.usage.data;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.Getter;

@Getter
public enum RemittanceStatus {
  PENDING("pending"),
  FAILED("failed"),
  SUCCEEDED("succeeded");

  private static final Map<String, RemittanceStatus> VALUE_ENUM_MAP =
      Arrays.stream(RemittanceStatus.values())
          .collect(Collectors.toMap(RemittanceStatus::getValue, Function.identity()));

  private final String value;

  RemittanceStatus(String value) {
    this.value = value;
  }

  /**
   * Parse the RemittanceStatus from its string representation
   *
   * @param value String representation of the RemittanceStatus, as seen in a host record
   * @return the RemittanceStatus enum
   */
  public static RemittanceStatus fromString(String value) {
    return VALUE_ENUM_MAP.get(value);
  }

  /** JPA converter for RemittanceStatus */
  @Converter(autoApply = true)
  public static class EnumConverter implements AttributeConverter<RemittanceStatus, String> {

    @Override
    public String convertToDatabaseColumn(RemittanceStatus attribute) {
      if (attribute == null) {
        return null;
      }
      return attribute.getValue();
    }

    @Override
    public RemittanceStatus convertToEntityAttribute(String dbData) {
      return Objects.nonNull(dbData) ? RemittanceStatus.fromString(dbData) : null;
    }
  }
}

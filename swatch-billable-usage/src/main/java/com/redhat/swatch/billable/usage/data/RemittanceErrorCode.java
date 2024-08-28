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
public enum RemittanceErrorCode {
  INACTIVE("inactive"),
  REDUNDANT("redundant"),
  SUBSCRIPTION_NOT_FOUND("subscription_not_found"),
  SUBSCRIPTION_TERMINATED("subscription_terminated"),
  USAGE_CONTEXT_LOOKUP("usage_context_lookup"),
  UNKNOWN("unknown");

  private static final Map<String, RemittanceErrorCode> VALUE_ENUM_MAP =
      Arrays.stream(RemittanceErrorCode.values())
          .collect(Collectors.toMap(RemittanceErrorCode::getValue, Function.identity()));

  private final String value;

  RemittanceErrorCode(String value) {
    this.value = value;
  }

  /**
   * Parse the RemittanceErrorCode from its string representation
   *
   * @param value String representation of the RemittanceErrorCode, as seen in a host record
   * @return the RemittanceErrorCode enum
   */
  public static RemittanceErrorCode fromString(String value) {
    return VALUE_ENUM_MAP.get(value);
  }

  /** JPA converter for RemittanceErrorCode */
  @Converter(autoApply = true)
  public static class EnumConverter implements AttributeConverter<RemittanceErrorCode, String> {

    @Override
    public String convertToDatabaseColumn(RemittanceErrorCode attribute) {
      if (attribute == null) {
        return null;
      }
      return attribute.getValue();
    }

    @Override
    public RemittanceErrorCode convertToEntityAttribute(String dbData) {
      return Objects.nonNull(dbData) ? RemittanceErrorCode.fromString(dbData) : null;
    }
  }
}

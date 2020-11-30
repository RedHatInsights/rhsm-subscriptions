/*
 * Copyright (c) 2019 - 2020 Red Hat, Inc.
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

import org.candlepin.subscriptions.utilization.api.model.UsageGenerated;

import com.google.common.collect.ImmutableMap;

import java.util.Map;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

/**
 * System purpose usage
 *
 * Usage represents the class of usage for a given system or subscription.
 */
public enum Usage {
    UNSPECIFIED(""),
    PRODUCTION("Production"),
    DEVELOPMENT_TEST("Development/Test"),
    DISASTER_RECOVERY("Disaster Recovery"),
    ANY("_ANY");

    private final String value;

    private static final Map<String, Usage> VALUE_ENUM_MAP = ImmutableMap.of(
        PRODUCTION.value.toUpperCase(), PRODUCTION,
        DEVELOPMENT_TEST.value.toUpperCase(), DEVELOPMENT_TEST,
        DISASTER_RECOVERY.value.toUpperCase(), DISASTER_RECOVERY
    );

    Usage(String value) {
        this.value = value;
    }

    /**
     * Parse the usage from its string representation (excluding special value _ANY)
     *
     * NOTE: this method will not return the special value ANY, and gives UNSPECIFIED for any invalid value.
     *
     * @param value String representation of the Usage, as seen in a host record
     * @return the Usage enum; UNSPECIFIED if unparseable.
     */
    public static Usage fromString(String value) {
        String key = value == null ? "" : value.toUpperCase();
        return VALUE_ENUM_MAP.getOrDefault(key, UNSPECIFIED);
    }

    /**
     * Parse the usage from its string representation.
     *
     * NOTE: this method will return UNSPECIFIED for any invalid value.
     *
     * @param value String representation of the Usage, as seen in the DB
     * @return the Usage enum; UNSPECIFIED if unparseable.
     */
    public static Usage fromDbString(String value) {
        if ("_ANY".equals(value)) {
            return Usage.ANY;
        }
        else {
            return fromString(value);
        }
    }

    public static Usage fromOpenApi(UsageGenerated usageGenerated) {
        return Usage.valueOf(usageGenerated.name());
    }

    public String getValue() {
        return value;
    }

    /**
     * JPA converter for Usage
     */
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
            return Usage.fromDbString(dbData);
        }
    }
}

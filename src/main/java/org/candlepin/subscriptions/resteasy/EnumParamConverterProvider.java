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
package org.candlepin.subscriptions.resteasy;

import org.springframework.stereotype.Component;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.ws.rs.ext.ParamConverter;
import javax.ws.rs.ext.ParamConverterProvider;
import javax.ws.rs.ext.Provider;

/**
 * ParamConverterProvider to enable use of enums in query providers.
 */
@Component
@Provider
public class EnumParamConverterProvider implements ParamConverterProvider {

    private static final Collection<Class<?>> caseInsensitiveParams = ResteasyConfiguration.caseInsensitiveDeserialization;

    @Override
    public <T> ParamConverter<T> getConverter(Class<T> rawType, Type genericType, Annotation[] annotations) {
        if (!rawType.isEnum()) {
            return null;
        }

        return caseInsensitiveParams.contains(rawType) ?
            new CaseInsensitiveEnumParamConverter<>(rawType) :
            new EnumParamConverter<>(rawType);
    }

    /**
     * ParamConverter that uses an ObjectMapper to convert enums to/from String.
     *
     * @param <T> the enum type
     */
    public static class EnumParamConverter<T> implements ParamConverter<T> {

        private final Map<String, T> stringValueMap = new HashMap<>();
        private final Class<T> clazz;

        public EnumParamConverter(Class<T> clazz) {

            this.clazz = clazz;
            for (T value : clazz.getEnumConstants()) {
                String stringValue = value.toString();
                stringValueMap.put(stringValue, value);
            }
        }

        @Override
        public T fromString(String value) {
            if (value == null) {
                return null;
            }
            if (stringValueMap.containsKey(value)) {
                return stringValueMap.get(value);
            }
            throw new IllegalArgumentException(String.format("%s is not a valid value for %s", value, clazz));
        }

        @Override
        public String toString(T value) {
            if (value == null) {
                return null;
            }
            return value.toString();
        }
    }


    //TODO try extending EnumParamConverter to get rid of duplicate code
    static class CaseInsensitiveEnumParamConverter<T> implements ParamConverter<T> {

        private final Class<T> className;
        private Map<String, T> keyValuePairs = new HashMap<>();

        public CaseInsensitiveEnumParamConverter(Class<T> className) {
            this.className = className;

            if (Objects.nonNull(className.getEnumConstants())) {
                keyValuePairs = Arrays.stream(className.getEnumConstants())
                    .collect(Collectors.toMap(T::toString, value -> value));
            }
        }

        @Override
        public T fromString(String value) {
            if (Objects.isNull(value)) {
                return null;
            }

            Optional<Map.Entry<String, T>> result = keyValuePairs.entrySet().stream()
                .filter(kv -> value.equalsIgnoreCase(kv.getKey())).findFirst();

            if (result.isPresent()) {
                return result.get().getValue();
            }

            throw new IllegalArgumentException(
                String.format("%s is not a valid value for %s", value, className));
        }

        @Override
        public String toString(T value) {
            return Objects.nonNull(value) ? value.toString() : null;
        }
    }
}

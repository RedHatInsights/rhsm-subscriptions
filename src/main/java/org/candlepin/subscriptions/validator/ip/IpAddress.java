/*
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
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
package org.candlepin.subscriptions.validator.ip;


import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import org.candlepin.subscriptions.validator.ip.IpAddress.List;

import java.lang.annotation.Documented;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import javax.validation.Constraint;
import javax.validation.Payload;

/**
 * Marks a field as needing V4/V6 IP validation. The annotation can be used on a String field
 * or on a String typed collection.
 * <pre>
 * @IpAddress
 * private String ip;
 *
 * private List<@IpAddress String> ipAddresses
 * </pre>
 */
@Target({ FIELD, TYPE_USE })
@Retention(RUNTIME)
@Repeatable(List.class)
@Documented
@Constraint(validatedBy = { IpAddressValidator.class })
public @interface IpAddress {
    String message() default "Must be a valid IP address.";
    Class<?>[] groups() default { };
    Class<? extends Payload>[] payload() default { };

    /**
     * Inner annotation to support annotating type arguments of parameterized types.
     */
    @Target({ FIELD, TYPE_USE })
    @Retention(RUNTIME)
    @Documented
    @interface List {
        IpAddress[] value();
    }
}

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
package org.candlepin.subscriptions.security;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.springframework.security.test.context.support.WithSecurityContext;

/**
 * Creates a mock Red Hat principal for testing, with account$value and orgId$value as account
 * number and org ID.
 *
 * <p>Defaults to granting ROLE_OPT_IN, but can be overridden.
 */
@Retention(RetentionPolicy.RUNTIME)
@WithSecurityContext(factory = WithMockInsightsUserSecurityContextFactory.class)
public @interface WithMockRedHatPrincipal {

  /**
   * Set account and orgId to account$value and owner$value respectively.
   *
   * @return
   */
  String value() default "";

  boolean nullifyAccount() default false;

  boolean nullifyOrgId() default false;

  String[] roles() default {"ROLE_" + RoleProvider.SWATCH_ADMIN_ROLE};
}

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

import javax.servlet.http.HttpServletRequest;

/**
 * This class includes GET requests in the list of HTTP verbs that must have a matching origin or
 * referrer. It exists because Jolokia will invoke JMX Beans with GET requests which we want to
 * protect from CSRF attacks.
 */
public class GetVerbIncludingAntiCsrfFilter extends AntiCsrfFilter {

  GetVerbIncludingAntiCsrfFilter(SecurityProperties props) {
    super(props);
  }

  @Override
  protected boolean requestVerbAllowed(HttpServletRequest request) {
    String verb = request.getMethod();
    return !(MODIFYING_VERBS.contains(verb) || "GET".equals(verb));
  }
}

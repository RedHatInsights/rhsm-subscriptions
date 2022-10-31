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

import org.candlepin.subscriptions.db.AccountConfigRepository;
import org.candlepin.subscriptions.exception.OptInRequiredException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * A simple class to check that the user has opted in. This class is meant to be used from a SpEL
 * expression like so "@optInChecker.checkAccess(authentication, request)". A WebExpressionVoter
 * will then run that expression and based on the result vote +1 or -1 for granting access. That
 * vote will got to the AccessDecisionManager who makes the final access control decision.
 */
@Component
public class OptInChecker {

  private final AccountConfigRepository accountConfigRepository;

  public OptInChecker(AccountConfigRepository accountConfigRepository) {
    this.accountConfigRepository = accountConfigRepository;
  }

  public boolean checkAccess(Authentication authentication) {
    Object principal = authentication.getPrincipal();
    if (!InsightsUserPrincipal.class.isAssignableFrom(principal.getClass())) {
      // Unrecognized principal.  Allow Spring Security to return Access Denied.
      return false;
    }

    InsightsUserPrincipal insightsUserPrincipal =
        (InsightsUserPrincipal) authentication.getPrincipal();

    /* If not opted-in, throw an exception.  Ideally we would just return true/false, but if we return
     * false the user just gets a generic "Access Denied" message.  By throwing the exception here, we
     * ensure that they see the message indicating they have not opted in. If we just wanted to return
     * true/false I think we would need to implement this class as an AccessDecisionVoter that throws
     * the OptInRequiredException in the AccessDecisionVoter.vote method and then our own
     * AbstractAccessDecisionManager capable of catching that exception and rethrowing it after all
     * the other voters had been consulted. */
    if (!accountConfigRepository.existsByOrgId(insightsUserPrincipal.getOrgId())) {
      throw new OptInRequiredException();
    }
    return true;
  }
}

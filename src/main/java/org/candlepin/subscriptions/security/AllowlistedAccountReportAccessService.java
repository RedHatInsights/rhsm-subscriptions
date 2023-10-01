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

import org.candlepin.subscriptions.db.model.OrgConfigRepository;
import org.candlepin.subscriptions.security.auth.ReportingAccessRequired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/**
 * Provides a means to validate that an authentication token has a allowlisted account associated
 * with it. The primary use of this class is to provide a check for expression based security
 * annotations.
 *
 * @see ReportingAccessRequired
 */
@Service("reportAccessService")
public class AllowlistedAccountReportAccessService {

  private final OrgConfigRepository orgConfigRepository;

  public AllowlistedAccountReportAccessService(OrgConfigRepository orgConfigRepository) {
    this.orgConfigRepository = orgConfigRepository;
  }

  public boolean providesAccessTo(Authentication auth) {
    InsightsUserPrincipal principal = (InsightsUserPrincipal) auth.getPrincipal();
    return orgConfigRepository.existsByOrgId(principal.getOrgId());
  }
}

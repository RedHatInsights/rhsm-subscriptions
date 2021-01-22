/*
 * Copyright (c) 2021 Red Hat, Inc.
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
package org.candlepin.subscriptions.cloudigrade;

import org.candlepin.subscriptions.cloudigrade.api.model.ConcurrencyReport;
import org.candlepin.subscriptions.cloudigrade.api.model.IdentityHeader;
import org.candlepin.subscriptions.cloudigrade.api.model.IdentityHeaderIdentity;
import org.candlepin.subscriptions.cloudigrade.api.model.IdentityHeaderIdentityUser;
import org.candlepin.subscriptions.cloudigrade.api.resources.ConcurrentApi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Base64;

/** Wrapper for cloudigrade concurrent API which handles header generation */
@Component
public class CloudigradeService {

  private final ConcurrentApi api;
  private final ObjectMapper objectMapper;
  Base64.Encoder b64Encoder;

  public CloudigradeService(ConcurrentApi api, ObjectMapper objectMapper) {
    this.api = api;
    this.objectMapper = objectMapper;
    b64Encoder = Base64.getEncoder();
  }

  public ConcurrencyReport listDailyConcurrentUsages(
      String accountNumber, Integer limit, Integer offset, LocalDate startDate, LocalDate endDate)
      throws ApiException {
    IdentityHeaderIdentityUser user = new IdentityHeaderIdentityUser().isOrgAdmin(true);
    IdentityHeaderIdentity identity =
        new IdentityHeaderIdentity().accountNumber(accountNumber).user(user);
    IdentityHeader identityHeader = new IdentityHeader().identity(identity);

    try {
      String headerString =
          b64Encoder.encodeToString(objectMapper.writeValueAsBytes(identityHeader));
      return api.listDailyConcurrentUsages(headerString, limit, offset, startDate, endDate);
    } catch (JsonProcessingException e) {
      throw new ApiException(e);
    }
  }
}

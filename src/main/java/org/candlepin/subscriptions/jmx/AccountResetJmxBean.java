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
package org.candlepin.subscriptions.jmx;

import javax.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.security.SecurityProperties;
import org.candlepin.subscriptions.tally.AccountResetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jmx.JmxException;
import org.springframework.jmx.export.annotation.ManagedOperation;
import org.springframework.jmx.export.annotation.ManagedOperationParameter;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.stereotype.Component;

/** JMX Bean for clearing/resetting account data. */
@Slf4j
@Component
@ManagedResource
public class AccountResetJmxBean {

  public static final String FEATURE_NOT_ENABLED_MESSSAGE = "Account Reset feature not enabled.";
  private final AccountResetService accountResetService;
  private final SecurityProperties properties;

  @Autowired
  public AccountResetJmxBean(
      AccountResetService accountResetService, SecurityProperties properties) {
    this.accountResetService = accountResetService;
    this.properties = properties;
  }

  @Transactional
  @ManagedOperation(
      description =
          "Clear tallies, hosts, and events for a given org ID.  Enabled via ENABLE_ACCOUNT_RESET environment variable.  Intended only for non-prod environments.")
  @ManagedOperationParameter(name = "orgId", description = "Organization ID")
  public String deleteDataAssociatedWithOrg(String orgId) {
    if (!properties.isResetAccountEnabled() && !properties.isDevMode()) {
      log.error(FEATURE_NOT_ENABLED_MESSSAGE);
      throw new JmxException(FEATURE_NOT_ENABLED_MESSSAGE);
    }

    log.info("Received request to delete all data associated with orgId {}", orgId);

    try {
      accountResetService.deleteDataForOrg(orgId);
    } catch (Exception e) {
      throw new JmxException("Unable to delete data for organization " + orgId, e);
    }

    var successMessage = "Finished deleting data associated with organization " + orgId;

    log.info(successMessage);

    return successMessage;
  }
}

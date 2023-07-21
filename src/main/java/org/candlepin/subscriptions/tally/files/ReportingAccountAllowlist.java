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
package org.candlepin.subscriptions.tally.files;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import org.candlepin.subscriptions.ApplicationProperties;
import org.candlepin.subscriptions.files.PerLineFileSource;
import org.candlepin.subscriptions.security.SecurityProperties;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/** Loads a list of account numbers that are permitted to access the Tally and Capacity API. */
@Component
public class ReportingAccountAllowlist implements ResourceLoaderAware {

  private PerLineFileSource source;
  private boolean isDevMode;

  public ReportingAccountAllowlist(
      ApplicationProperties props, SecurityProperties securityProps, ApplicationClock clock) {
    String resourceLocation = props.getReportingAccountAllowlistResourceLocation();
    source =
        resourceLocation != null
            ? new PerLineFileSource(
                resourceLocation, clock.getClock(), props.getReportingAccountAllowlistCacheTtl())
            : null;
    this.isDevMode = securityProps.isDevMode();
  }

  public boolean hasAccount(String account) throws IOException {
    // Allowlist any account when running in dev mode!
    if (isDevMode) {
      return true;
    }

    // Currently this list is read on each request. If this presents a problem in the
    // future, we should consider caching the account list.
    return source != null && source.list().contains(account);
  }

  @Override
  public void setResourceLoader(ResourceLoader resourceLoader) {
    if (source != null) {
      source.setResourceLoader(resourceLoader);
    }
  }

  @PostConstruct
  public void init() {
    if (source != null) {
      source.init();
    }
  }
}

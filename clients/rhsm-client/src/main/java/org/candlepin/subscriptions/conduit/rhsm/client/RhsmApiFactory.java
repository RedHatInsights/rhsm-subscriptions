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
package org.candlepin.subscriptions.conduit.rhsm.client;

import org.candlepin.subscriptions.conduit.rhsm.client.resources.RhsmApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.FactoryBean;

/**
 * Builds an RhsmApi, which may be a stub, or a normal client, with or without cert auth depending
 * on properties.
 */
public class RhsmApiFactory implements FactoryBean<RhsmApi> {
  private static Logger log = LoggerFactory.getLogger(RhsmApiFactory.class);

  private final RhsmApiProperties properties;

  public RhsmApiFactory(RhsmApiProperties properties) {
    this.properties = properties;
  }

  @Override
  public RhsmApi getObject() throws Exception {
    if (properties.isUseStub()) {
      log.info("Using stub RHSM client");
      return new StubRhsmApi();
    }

    ApiClient client;
    if (properties.usesClientAuth()) {
      log.info("RHSM client configured with client-cert auth");
      client = new RhsmX509ApiFactory(properties).getObject();
    } else {
      log.info("RHSM client configured without client-cert auth");
      client = new ApiClient();
    }
    if (properties.getUrl() != null) {
      log.info("RHSM URL: {}", properties.getUrl());
      client.setBasePath(properties.getUrl());
      client.addDefaultHeader("cp-lookup-permissions", "false");
    } else {
      log.warn("RHSM URL not set...");
    }
    return new RhsmApi(client);
  }

  @Override
  public Class<?> getObjectType() {
    return RhsmApi.class;
  }
}

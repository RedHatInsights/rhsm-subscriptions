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
package com.redhat.swatch.common.resteasy.logging;

import static com.redhat.swatch.configuration.util.Constants.IQE_TEST_HEADER;

import io.quarkus.arc.profile.UnlessBuildProfile;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;
import org.jboss.logmanager.MDC;

/**
 * This filter propagates the HTTP header `x-rh-iqe-test` to the MDC context. In case of the header
 * is not set, it will be cleared it. This is meant to be used only for tests purposes, so not
 * enabled in prod profile.
 */
@UnlessBuildProfile("prod")
@Provider
public class IqeTestMdcLoggingContainerRequestFilter implements ContainerRequestFilter {

  @Override
  public void filter(ContainerRequestContext requestContext) throws IOException {
    String iqeTest = requestContext.getHeaderString(IQE_TEST_HEADER);
    MDC.put(IQE_TEST_HEADER, iqeTest);
  }
}

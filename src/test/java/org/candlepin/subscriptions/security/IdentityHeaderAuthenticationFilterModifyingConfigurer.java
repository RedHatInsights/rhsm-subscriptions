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

import org.springframework.security.config.BeanIds;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.ConfigurableMockMvcBuilder;
import org.springframework.test.web.servlet.setup.MockMvcConfigurerAdapter;
import org.springframework.web.context.WebApplicationContext;

/**
 * This class sets the checkForPrincipalChanges field to false in the {@link
 * IdentityHeaderAuthenticationFilter} that we have in the Spring Security filter chain. We want to
 * do this because we want to use the SecurityContext establish with our test annotations. If we
 * don't set checkForPrincipalChanges to false, the {@link
 * org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter}
 * will look into our mock request for the pre-authentication information (and won't find it because
 * we're setting that info in the annotation and not in each request) and will then engage the
 * authentication flow for the URL. Basically, this class is telling the
 * IdentityHeaderAuthenticationFilter, "Trust what's in the SecurityContext you get and don't worry
 * about it."
 */
public class IdentityHeaderAuthenticationFilterModifyingConfigurer
    extends MockMvcConfigurerAdapter {

  @Override
  public RequestPostProcessor beforeMockMvcCreated(
      ConfigurableMockMvcBuilder<?> builder, WebApplicationContext context) {
    FilterChainProxy chainProxy =
        (FilterChainProxy) context.getBean(BeanIds.SPRING_SECURITY_FILTER_CHAIN);

    return request -> {
      /* Given a request, look at the FilterChainProxy.  For every chain, go through all filters and
       * search for the instance of the IdentityHeaderAuthenticationFilter.  Take the found instance
       * and set checkForPrincipalChanges to false
       */
      chainProxy
          .getFilterChains()
          .forEach(
              chain -> {
                var filter =
                    chain.getFilters().stream()
                        .filter(x -> x instanceof IdentityHeaderAuthenticationFilter)
                        .findFirst()
                        .get();
                ((IdentityHeaderAuthenticationFilter) filter).setCheckForPrincipalChanges(false);
              });
      return request;
    };
  }
}

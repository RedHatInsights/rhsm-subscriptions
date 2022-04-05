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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.candlepin.subscriptions.rbac.RbacProperties;
import org.candlepin.subscriptions.rbac.RbacService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.web.csrf.CsrfFilter;

/**
 * Security configuration for Internal APIs.
 *
 * <p>This security configuration will be loaded before the normal configuration.
 */
@Order(2)
@Configuration
public class InternalApiSecurityConfiguration extends WebSecurityConfigurerAdapter {

  @Autowired private SecurityProperties secProps;
  @Autowired private ConfigurableEnvironment env;
  @Autowired private ObjectMapper mapper;
  @Autowired private RbacProperties rbacProperties;
  @Autowired private RbacService rbacService;

  @Override
  protected void configure(AuthenticationManagerBuilder auth) throws Exception {
    auth.authenticationProvider(
        internalIdentityHeaderAuthenticationProvider(
            internalIdentityHeaderAuthenticationDetailsService(
                secProps, rbacProperties, rbacService)));
  }

  @Bean
  public AuthenticationProvider internalIdentityHeaderAuthenticationProvider(
      @Qualifier("internalIdentityHeaderAuthenticationDetailsService")
          IdentityHeaderAuthenticationDetailsService detailsService) {
    return new IdentityHeaderAuthenticationProvider(detailsService);
  }

  @Bean
  public IdentityHeaderAuthenticationDetailsService
      internalIdentityHeaderAuthenticationDetailsService(
          SecurityProperties secProps, RbacProperties rbacProperties, RbacService rbacService) {
    return new IdentityHeaderAuthenticationDetailsService(
        secProps, rbacProperties, internalIdentityHeaderAuthoritiesMapper(), rbacService);
  }

  @Bean
  public IdentityHeaderAuthoritiesMapper internalIdentityHeaderAuthoritiesMapper() {
    return new IdentityHeaderAuthoritiesMapper();
  }

  // NOTE: intentionally *not* annotated with @Bean; @Bean causes an extra use as an application
  // filter
  public AntiCsrfFilter antiCsrfFilter(SecurityProperties secProps, ConfigurableEnvironment env) {
    return new AntiCsrfFilter(secProps, env);
  }

  // NOTE: intentionally *not* annotated w/ @Bean; @Bean causes an *extra* use as an application
  // filter
  public IdentityHeaderAuthenticationFilter internalIdentityHeaderAuthenticationFilter()
      throws Exception {
    IdentityHeaderAuthenticationFilter filter = new IdentityHeaderAuthenticationFilter(mapper);
    filter.setCheckForPrincipalChanges(true);
    filter.setAuthenticationManager(authenticationManager());
    filter.setAuthenticationFailureHandler(new IdentityHeaderAuthenticationFailureHandler(mapper));
    filter.setContinueFilterChainOnUnsuccessfulAuthentication(false);
    return filter;
  }

  // NOTE: intentionally *not* annotated w/ @Bean; @Bean causes an *extra* use as an application
  // filter
  public MdcFilter internalMdcFilter() {
    return new MdcFilter();
  }

  @Override
  protected void configure(HttpSecurity http) throws Exception {
    // See
    // https://docs.spring.io/spring-security/site/docs/current/reference/html5/#ns-custom-filters
    // for list of filters and their order
    http.requestMatchers(matchers -> matchers.antMatchers("/**/internal/**"))
        .csrf()
        .disable()
        .addFilter(internalIdentityHeaderAuthenticationFilter())
        .addFilterAfter(internalMdcFilter(), IdentityHeaderAuthenticationFilter.class)
        .addFilterAt(antiCsrfFilter(secProps, env), CsrfFilter.class)
        .authorizeRequests()
        .anyRequest()
        .authenticated();
  }
}

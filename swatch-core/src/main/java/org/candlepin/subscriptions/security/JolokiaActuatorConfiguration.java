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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfFilter;

/**
 * Security configuration for Jolokia Actuator. Jolokia has GET endpoints that can affect the state
 * of the application. We need to protect these endpoints from CSRF attacks. Normally GET requests
 * are exempted from CSRF restrictions, so we need to create a special security configuration and
 * use a special AntiCsrfFilter to guard GET requests on the /actuator/jolokia context.
 *
 * <p>This security configuration will be loaded before the normal configuration.
 */
@Order(1)
@Configuration
public class JolokiaActuatorConfiguration {

  // NOTE: intentionally *not* annotated with @Bean; @Bean causes an extra use as an application
  // filter
  public AntiCsrfFilter getVerbIncludingAntiCsrfFilter(
      SecurityProperties appProps, ConfigurableEnvironment env) {
    return new GetVerbIncludingAntiCsrfFilter(appProps, env);
  }

  // NOTE: intentionally *not* annotated w/ @Bean; @Bean causes an *extra* use as an application
  // filter
  public IdentityHeaderAuthenticationFilter identityHeaderAuthenticationFilter(
      AuthenticationManager authenticationManager, ObjectMapper mapper) {
    IdentityHeaderAuthenticationFilter filter = new IdentityHeaderAuthenticationFilter(mapper);
    filter.setCheckForPrincipalChanges(true);
    filter.setAuthenticationManager(authenticationManager);
    filter.setAuthenticationFailureHandler(new IdentityHeaderAuthenticationFailureHandler(mapper));
    filter.setContinueFilterChainOnUnsuccessfulAuthentication(false);
    return filter;
  }

  // NOTE: intentionally *not* annotated w/ @Bean; @Bean causes an *extra* use as an application
  // filter
  public MdcFilter mdcFilter() {
    return new MdcFilter();
  }

  @Bean
  public AuthenticationProvider jolokiaIdentityHeaderAuthenticationProvider(
      @Qualifier("jolokiaIdentityHeaderAuthenticationDetailsService")
          IdentityHeaderAuthenticationDetailsService detailsService,
      IdentityHeaderAuthoritiesMapper conduitIdentityHeaderAuthoritiesMapper,
      AuthProperties conduitAuthProperties) {
    return new IdentityHeaderAuthenticationProvider(
        detailsService, conduitIdentityHeaderAuthoritiesMapper, conduitAuthProperties);
  }

  @Bean
  public IdentityHeaderAuthenticationDetailsService
      jolokiaIdentityHeaderAuthenticationDetailsService(
          SecurityProperties secProps,
          IdentityHeaderAuthoritiesMapper jolokiaIdentityHeaderAuthoritiesMapper) {
    // NOTE: we use empty rbac properties and null rbac controller because jolokia access doesn't
    // use RBAC service.
    return new IdentityHeaderAuthenticationDetailsService(
        secProps, new RbacProperties(), jolokiaIdentityHeaderAuthoritiesMapper, null);
  }

  @Bean
  public IdentityHeaderAuthoritiesMapper jolokiaIdentityHeaderAuthoritiesMapper() {
    return new IdentityHeaderAuthoritiesMapper();
  }

  @Bean
  AuthProperties conduitAuthProperties() {
    return new AuthProperties();
  }

  @Bean
  public IdentityHeaderAuthoritiesMapper conduitIdentityHeaderAuthoritiesMapper() {
    return new IdentityHeaderAuthoritiesMapper();
  }

  @Bean
  public SecurityFilterChain jolokiaFilterChain(
      HttpSecurity http,
      SecurityProperties secProps,
      AuthenticationManager authenticationManager,
      ConfigurableEnvironment env,
      ObjectMapper mapper)
      throws Exception {
    // See
    // https://docs.spring.io/spring-security/site/docs/current/reference/html5/#ns-custom-filters
    // for list of filters and their order
    http.requestMatcher(EndpointRequest.to("jolokia"))
        .addFilter(identityHeaderAuthenticationFilter(authenticationManager, mapper))
        .addFilterAfter(mdcFilter(), IdentityHeaderAuthenticationFilter.class)
        .addFilterAt(getVerbIncludingAntiCsrfFilter(secProps, env), CsrfFilter.class)
        .csrf(csrf -> csrf.disable())
        .authorizeHttpRequests(requests -> requests.anyRequest().authenticated());

    return http.build();
  }
}

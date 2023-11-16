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
package org.candlepin.subscriptions.conduit.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.candlepin.subscriptions.rbac.RbacProperties;
import org.candlepin.subscriptions.rbac.RbacService;
import org.candlepin.subscriptions.security.AntiCsrfFilter;
import org.candlepin.subscriptions.security.AuthProperties;
import org.candlepin.subscriptions.security.IdentityHeaderAuthenticationDetailsService;
import org.candlepin.subscriptions.security.IdentityHeaderAuthenticationFailureHandler;
import org.candlepin.subscriptions.security.IdentityHeaderAuthenticationFilter;
import org.candlepin.subscriptions.security.IdentityHeaderAuthenticationProvider;
import org.candlepin.subscriptions.security.IdentityHeaderAuthoritiesMapper;
import org.candlepin.subscriptions.security.LogPrincipalFilter;
import org.candlepin.subscriptions.security.MdcFilter;
import org.candlepin.subscriptions.security.RestAccessDeniedHandler;
import org.candlepin.subscriptions.security.RestAuthenticationEntryPoint;
import org.candlepin.subscriptions.security.SecurityProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.boot.actuate.autoconfigure.web.server.ManagementServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/**
 * Configuration class for Spring Security.
 *
 * <p>The architecture here can be confusing due to our use of a custom filter. Here is a discussion
 * of the Spring Security architecture:
 * https://spring.io/guides/topicals/spring-security-architecture In our case, requests are
 * pre-authenticated and the relevant information is stored in a custom header.
 *
 * <p>We add the IdentityHeaderAuthenticationFilter as a filter to the Spring Security
 * FilterChainProxy that sits in the standard servlet filter chain and delegates out to all the
 * various Spring Security filters.
 *
 * <ol>
 *   <li>IdentityHeaderAuthenticationFilter parses the servlet request to create the
 *       InsightsUserPrincipal and places it in an Authentication object.
 *   <li>IdentityHeaderAuthenticationFilter's superclass invokes the
 *       IdentityHeaderAuthenticationDetailsSource to populate the Authentication object with the
 *       PreAuthenticatedGrantedAuthoritiesWebAuthenticationDetails which contain the roles.
 *   <li>The Authentication object is passed to the Spring Security AuthenticationManager. In this
 *       case, we're using a ProviderManager with one AuthenticationProvider,
 *       IdentityHeaderAuthenticationProvider, installed.
 *   <li>The IdentityHeaderAuthenticationProvider is invoked to build a blessed Authentication
 *       object. We examine the current Authentication and make sure everything we expect is there.
 *       Then we take the granted authorities provided from the
 *       IdentityHeaderAuthenticationDetailsSource and push them and the InsightsUserPrincipal into
 *       a new, blessed Authentication object and return it.
 * </ol>
 */
@Configuration
@Import(RbacConfiguration.class)
public class ApiSecurityConfiguration {

  @Autowired protected ManagementServerProperties actuatorProps;

  private static final String[] URLS_PERMITTED_WITHOUT_AUTH =
      new String[] {
        "/**/*openapi.yaml",
        "/**/*openapi.json",
        "/**/version",
        "/api-docs/**",
        "/webjars/**",
        "/api/swatch-system-conduit/internal/swagger-ui/index.html"
      };

  // NOTE: intentionally *not* annotated with @Bean; @Bean causes an extra use as an application
  // filter
  public AntiCsrfFilter antiCsrfFilter(SecurityProperties appProps) {
    return new AntiCsrfFilter(appProps);
  }

  @Bean
  AuthProperties authProperties() {
    return new AuthProperties();
  }

  // NOTE: intentionally not annotated w/ @Bean; @Bean causes an extra use as an application filter
  public IdentityHeaderAuthenticationFilter identityHeaderAuthenticationFilter(
      AuthenticationManager authenticationManager, ObjectMapper mapper) {
    IdentityHeaderAuthenticationFilter filter = new IdentityHeaderAuthenticationFilter(mapper);
    filter.setCheckForPrincipalChanges(true);
    filter.setAuthenticationManager(authenticationManager);
    filter.setAuthenticationFailureHandler(new IdentityHeaderAuthenticationFailureHandler(mapper));
    filter.setContinueFilterChainOnUnsuccessfulAuthentication(false);
    return filter;
  }

  // NOTE: intentionally not annotated w/ @Bean; @Bean causes an extra use as an application filter
  public MdcFilter mdcFilter() {
    return new MdcFilter();
  }

  // NOTE: intentionally not annotated w/ @Bean; @Bean causes an extra use as an application filter
  public LogPrincipalFilter logPrincipalFilter() {
    return new LogPrincipalFilter();
  }

  @Bean
  public IdentityHeaderAuthenticationFailureHandler identityHeaderAuthenticationFailureHandler(
      ObjectMapper mapper) {
    return new IdentityHeaderAuthenticationFailureHandler(mapper);
  }

  @Bean
  public AccessDeniedHandler restAccessDeniedHandler(ObjectMapper mapper) {
    return new RestAccessDeniedHandler(mapper);
  }

  @Bean
  public AuthenticationEntryPoint restAuthenticationEntryPoint(
      IdentityHeaderAuthenticationFailureHandler identityHeaderAuthenticationFailureHandler) {
    return new RestAuthenticationEntryPoint(identityHeaderAuthenticationFailureHandler);
  }

  @Bean
  public IdentityHeaderAuthoritiesMapper identityHeaderAuthoritiesMapper() {
    return new IdentityHeaderAuthoritiesMapper();
  }

  @Bean
  public IdentityHeaderAuthenticationDetailsService identityHeaderAuthenticationDetailsService(
      SecurityProperties secProps,
      RbacProperties rbacProperties,
      RbacService rbacService,
      IdentityHeaderAuthoritiesMapper identityHeaderAuthoritiesMapper) {
    return new IdentityHeaderAuthenticationDetailsService(
        secProps, rbacProperties, identityHeaderAuthoritiesMapper, rbacService);
  }

  @Bean
  public AuthenticationProvider identityHeaderAuthenticationProvider(
      @Qualifier("identityHeaderAuthenticationDetailsService")
          IdentityHeaderAuthenticationDetailsService detailsService,
      AuthProperties authProperties,
      IdentityHeaderAuthoritiesMapper identityHeaderAuthoritiesMapper) {
    return new IdentityHeaderAuthenticationProvider(
        detailsService, identityHeaderAuthoritiesMapper, authProperties);
  }

  @Bean
  public AuthenticationManager authenticationManager(
      HttpSecurity http, AuthenticationProvider identityHeaderAuthenticationProvider)
      throws Exception {
    AuthenticationManagerBuilder authenticationManagerBuilder =
        http.getSharedObject(AuthenticationManagerBuilder.class);
    AuthenticationManager parentAuthenticationManager =
        http.getSharedObject(AuthenticationManager.class);
    authenticationManagerBuilder.authenticationProvider(identityHeaderAuthenticationProvider);
    authenticationManagerBuilder.parentAuthenticationManager(parentAuthenticationManager);
    return authenticationManagerBuilder.build();
  }

  /**
   * Check for DummmyRequest in case of below issue forwarding to /error: <a
   * href="https://stackoverflow.com/a/71695378">See this Stack Overflow question</a>
   */
  // We can't use instance of for the class check since DummyRequest isn't publicly visible
  @SuppressWarnings("java:S1872")
  private boolean isDummyRequest(HttpServletRequest request) {
    return !request
            .getClass()
            .getName()
            .equals("org.springframework.security.web.FilterInvocation$DummyRequest")
        && request.getServerPort() == actuatorProps.getPort()
        && request.getContextPath().equals(actuatorProps.getBasePath());
  }

  @Bean
  public SecurityFilterChain conduitFilterChain(
      HttpSecurity http,
      SecurityProperties secProps,
      AuthenticationManager authenticationManager,
      AccessDeniedHandler restAccessDeniedHandler,
      AuthenticationEntryPoint restAuthenticationEntryPoint,
      ObjectMapper mapper)
      throws Exception {
    http.addFilter(identityHeaderAuthenticationFilter(authenticationManager, mapper))
        .addFilterAfter(mdcFilter(), IdentityHeaderAuthenticationFilter.class)
        .addFilterAfter(logPrincipalFilter(), MdcFilter.class)
        .addFilterAt(antiCsrfFilter(secProps), CsrfFilter.class)
        .csrf(csrf -> csrf.disable())
        .exceptionHandling(
            handler -> {
              handler.accessDeniedHandler(restAccessDeniedHandler);
              handler.authenticationEntryPoint(restAuthenticationEntryPoint);
            })
        // disable sessions, our API is stateless, and sessions cause RBAC information to be cached
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            requests -> {
              for (String url : URLS_PERMITTED_WITHOUT_AUTH) {
                requests.requestMatchers(new AntPathRequestMatcher(url)).permitAll();
              }
              requests.requestMatchers(this::isDummyRequest).permitAll();

              requests
                  .requestMatchers(EndpointRequest.to("health", "info", "prometheus"))
                  .permitAll();
              /* Values assigned to management.path-mapping.* shouldn't have a leading slash. However, Clowder
               * only provides a path starting with a leading slash.  I have elected to set the default
               * to do the same for the sake of consistency.  The leading slash can potentially cause problems with Spring
               * Security since the path now becomes (assuming management.base-path is "/") "//metrics".
               * Browser requests to "/metrics" aren't going to match according to Spring Security's path matching rules
               * and the end result is that any security rule applied to EndpointRequest.to("prometheus") will be
               * applied to the defined path ("//metrics") rather than the de facto path ("/metrics").
               * Accordingly, I've put in a custom rule in the security config to allow for access to "/metrics"
               */
              requests.requestMatchers(new AntPathRequestMatcher("/metrics")).permitAll();
              // Intentionally not prefixed with "ROLE_"
              requests
                  .requestMatchers(new AntPathRequestMatcher("/**/internal/**"))
                  .hasRole("INTERNAL");
              requests.anyRequest().authenticated();
            })
        // Creates an anonymous user if no header is present at all. Prevents NPEs
        .anonymous(Customizer.withDefaults());
    return http.build();
  }
}

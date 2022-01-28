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
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfFilter;

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
public class ApiSecurityConfiguration extends WebSecurityConfigurerAdapter {

  @Autowired protected ObjectMapper mapper;
  @Autowired protected SecurityProperties secProps;
  @Autowired protected RbacProperties rbacProperties;
  @Autowired protected ConfigurableEnvironment env;
  @Autowired protected RbacService rbacService;

  @Override
  public void configure(AuthenticationManagerBuilder auth) {
    // Add our AuthenticationProvider to the Provider Manager's list
    auth.authenticationProvider(
        identityHeaderAuthenticationProvider(
            identityHeaderAuthenticationDetailsService(secProps, rbacProperties, rbacService)));
  }

  @Bean
  public IdentityHeaderAuthenticationDetailsService identityHeaderAuthenticationDetailsService(
      SecurityProperties secProps, RbacProperties rbacProperties, RbacService rbacService) {
    return new IdentityHeaderAuthenticationDetailsService(
        secProps, rbacProperties, identityHeaderAuthoritiesMapper(), rbacService);
  }

  @Bean
  public AuthenticationProvider identityHeaderAuthenticationProvider(
      @Qualifier("identityHeaderAuthenticationDetailsService")
          IdentityHeaderAuthenticationDetailsService detailsService) {
    return new IdentityHeaderAuthenticationProvider(detailsService);
  }

  // NOTE: intentionally *not* annotated w/ @Bean; @Bean causes an *extra* use as an application
  // filter
  public IdentityHeaderAuthenticationFilter identityHeaderAuthenticationFilter() throws Exception {
    IdentityHeaderAuthenticationFilter filter = new IdentityHeaderAuthenticationFilter(mapper);
    filter.setCheckForPrincipalChanges(true);
    filter.setAuthenticationManager(authenticationManager());
    filter.setAuthenticationFailureHandler(new IdentityHeaderAuthenticationFailureHandler(mapper));
    filter.setContinueFilterChainOnUnsuccessfulAuthentication(false);
    return filter;
  }

  @Bean
  public IdentityHeaderAuthoritiesMapper identityHeaderAuthoritiesMapper() {
    return new IdentityHeaderAuthoritiesMapper();
  }

  @Bean
  public AccessDeniedHandler restAccessDeniedHandler() {
    return new RestAccessDeniedHandler(mapper);
  }

  @Bean
  public AuthenticationEntryPoint restAuthenticationEntryPoint() {
    return new RestAuthenticationEntryPoint(new IdentityHeaderAuthenticationFailureHandler(mapper));
  }

  // NOTE: intentionally *not* annotated with @Bean; @Bean causes an extra use as an application
  // filter
  public AntiCsrfFilter antiCsrfFilter(SecurityProperties secProps, ConfigurableEnvironment env) {
    return new AntiCsrfFilter(secProps, env);
  }

  // NOTE: intentionally *not* annotated w/ @Bean; @Bean causes an *extra* use as an application
  // filter
  public MdcFilter mdcFilter() {
    return new MdcFilter();
  }

  @Override
  protected void configure(HttpSecurity http) throws Exception {
    String apiPath =
        env.getRequiredProperty(
            "rhsm-subscriptions.package_uri_mappings.org.candlepin.subscriptions.resteasy");
    http.addFilter(identityHeaderAuthenticationFilter())
        .addFilterAfter(mdcFilter(), IdentityHeaderAuthenticationFilter.class)
        .addFilterAt(antiCsrfFilter(secProps, env), CsrfFilter.class)
        .csrf()
        .disable()
        .exceptionHandling()
        .accessDeniedHandler(restAccessDeniedHandler())
        .authenticationEntryPoint(restAuthenticationEntryPoint())
        .and()
        // disable sessions, our API is stateless, and sessions cause RBAC information to be
        // cached
        .sessionManagement()
        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
        .and()
        .anonymous() // Creates an anonymous user if no header is present at all. Prevents NPEs
        .and()
        .authorizeRequests()
        .antMatchers("/**/openapi.*", "/**/version", "/api-docs/**", "/webjars/**")
        .permitAll()
        // ingress security uses server settings (require ssl cert auth), so permit all here
        .antMatchers(String.format("/%s/ingress/**", apiPath))
        .permitAll()
        .requestMatchers(EndpointRequest.to("health", "info", "prometheus", "hawtio"))
        .permitAll()

        /* Values assigned to management.path-mapping.* shouldn't have a leading slash. However, Clowder
         * only provides a path starting with a leading slash.  I have elected to set the default
         * to do the same for the sake of consistency.  The leading slash can potentially cause problems with Spring
         * Security since the path now becomes (assuming management.base-path is "/") "//metrics".
         * Browser requests to "/metrics" aren't going to match according to Spring Security's path matching rules
         * and the end result is that any security rule applied to EndpointRequest.to("prometheus") will be
         * applied to the defined path ("//metrics") rather than the de facto path ("/metrics").
         * Accordingly, I've put in a custom rule in the security config to allow for access to "/metrics"
         */

        .antMatchers("/metrics")
        .permitAll()
        .antMatchers("/**/capacity/**", "/**/tally/**", "/**/hosts/**")
        .access("@optInChecker.checkAccess(authentication)")
        .anyRequest()
        .authenticated();
  }
}

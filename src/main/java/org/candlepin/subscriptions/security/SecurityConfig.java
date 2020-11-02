/*
 * Copyright (c) 2019 - 2019 Red Hat, Inc.
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

import org.candlepin.subscriptions.ApplicationProperties;
import org.candlepin.subscriptions.controller.OptInController;
import org.candlepin.subscriptions.rbac.RbacService;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.logout.LogoutFilter;

import java.util.Arrays;

/**
 * Holder class for security configurations
 */
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    protected SecurityConfig() {
        // Container class only
    }

    /**
     * Configuration class for Spring Security.
     *
     * The architecture here can be confusing due to our use of a custom filter.  Here is a discussion of the
     * Spring Security architecture: https://spring.io/guides/topicals/spring-security-architecture  In our
     * case, requests are pre-authenticated and the relevant information is stored in a custom header.
     *
     * We add the IdentityHeaderAuthenticationFilter as a filter to the Spring Security FilterChainProxy that
     * sits in the standard servlet filter chain and delegates out to all the various Spring Security filters.
     * <ol>
     *   <li>IdentityHeaderAuthenticationFilter parses the servlet request to create the InsightsUserPrincipal
     *       and places it in an Authentication object.</li>
     *   <li>IdentityHeaderAuthenticationFilter's superclass invokes the
     *       IdentityHeaderAuthenticationDetailsSource to populate the Authentication object with the
     *       PreAuthenticatedGrantedAuthoritiesWebAuthenticationDetails which contain the roles.</li>
     *   <li>The Authentication object is passed to the Spring Security AuthenticationManager.  In this case,
     *       we're using a ProviderManager with one AuthenticationProvider,
     *       IdentityHeaderAuthenticationProvider, installed.</li>
     *   <li>
     *       The IdentityHeaderAuthenticationProvider is invoked to build a blessed Authentication object.  We
     *       examine the current Authentication and make sure everything we expect is there.  Then we take the
     *       granted authorities provided from the IdentityHeaderAuthenticationDetailsSource and push them and
     *       the InsightsUserPrincipal into a new, blessed Authentication object and return it.</li>
     * </ol>
     */
    @Configuration
    public static class ApiConfiguration extends WebSecurityConfigurerAdapter {
        @Autowired protected ObjectMapper mapper;
        @Autowired protected ApplicationProperties appProps;
        @Autowired protected ConfigurableEnvironment env;
        @Autowired protected RbacService rbacService;

        @Override
        public void configure(AuthenticationManagerBuilder auth) {
            // Add our AuthenticationProvider to the Provider Manager's list
            auth.authenticationProvider(identityHeaderAuthenticationProvider(
                identityHeaderAuthenticationDetailsService(appProps, rbacService)));
        }

        @Bean
        public IdentityHeaderAuthenticationDetailsService identityHeaderAuthenticationDetailsService(
            ApplicationProperties appProps, RbacService rbacService) {
            return new IdentityHeaderAuthenticationDetailsService(appProps, identityHeaderAuthoritiesMapper(),
                rbacService);
        }

        @Bean
        public AuthenticationProvider identityHeaderAuthenticationProvider(
            IdentityHeaderAuthenticationDetailsService detailsService) {

            return new IdentityHeaderAuthenticationProvider(detailsService);
        }

        @Bean
        public RbacService rbacService() {
            return new RbacService();
        }

        // NOTE: intentionally *not* annotated w/ @Bean; @Bean causes an *extra* use as an application filter
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

        // NOTE: intentionally *not* annotated with @Bean; @Bean causes an extra use as an application filter
        public AntiCsrfFilter antiCsrfFilter(ApplicationProperties appProps, ConfigurableEnvironment env) {
            return new AntiCsrfFilter(appProps, env);
        }

        @Bean
        public FilterRegistrationBean<OptInFilter> optInFilterRegistration(OptInController optInController) {
            String apiPath = env.getRequiredProperty(
                "rhsm-subscriptions.package_uri_mappings.org.candlepin.subscriptions");

            FilterRegistrationBean<OptInFilter> frb = new FilterRegistrationBean<>();
            frb.setFilter(new OptInFilter(optInController));
            frb.setUrlPatterns(Arrays.asList(
                String.format("/%s/capacity/*", apiPath),
                String.format("/%s/tally/*", apiPath),
                String.format("/%s/hosts/*", apiPath)
            ));
            return frb;
        }

        @Override
        protected void configure(HttpSecurity http) throws Exception {
            String apiPath = env.getRequiredProperty(
                "rhsm-subscriptions.package_uri_mappings.org.candlepin.subscriptions");
            http
                .addFilter(identityHeaderAuthenticationFilter())
                .addFilterAfter(antiCsrfFilter(appProps, env), IdentityHeaderAuthenticationFilter.class)
                .csrf().disable()
                .exceptionHandling()
                    .accessDeniedHandler(restAccessDeniedHandler())
                    .authenticationEntryPoint(restAuthenticationEntryPoint())
                .and()
                // disable sessions, our API is stateless, and sessions cause RBAC information to be cached
                .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                .and()
                .anonymous() // Creates an anonymous user if no header is present at all. Prevents NPEs
                .and()
                .authorizeRequests()
                    .antMatchers("/**/openapi.*", "/**/version", "/api-docs/**", "/webjars/**").permitAll()
                    // ingress security uses server settings (require ssl cert auth), so permit all here
                    .antMatchers(String.format("/%s/ingress/**", apiPath)).permitAll()
                    .requestMatchers(EndpointRequest.to(
                        "health", "info", "prometheus", "hawtio"
                    )).permitAll()
                    .anyRequest().authenticated();
        }
    }

    /**
     * Security configuration for Jolokia Actuator.  Jolokia has GET endpoints that can affect the state of
     * the application.  We need to protect these endpoints from CSRF attacks.  Normally GET requests are
     * exempted from CSRF restrictions, so we need to create a special security configuration and use a
     * special AntiCsrfFilter to guard GET requests on the /actuator/jolokia context.
     *
     * This security configuration will be loaded before the normal configuration.
     */
    @Order(1)
    @Configuration
    @Import(ApiConfiguration.class)
    public static class JolokiaActuatorConfiguration extends WebSecurityConfigurerAdapter {
        @Autowired private ApplicationProperties appProps;
        @Autowired private ConfigurableEnvironment env;
        @Autowired protected ObjectMapper mapper;

        // NOTE: intentionally *not* annotated with @Bean; @Bean causes an extra use as an application filter
        public AntiCsrfFilter getVerbIncludingAntiCsrfFilter(ApplicationProperties appProps,
            ConfigurableEnvironment env) {
            return new GetVerbIncludingAntiCsrfFilter(appProps, env);
        }


        // NOTE: intentionally *not* annotated w/ @Bean; @Bean causes an *extra* use as an application filter
        public IdentityHeaderAuthenticationFilter identityHeaderAuthenticationFilter() throws Exception {
            IdentityHeaderAuthenticationFilter filter = new IdentityHeaderAuthenticationFilter(mapper);
            filter.setCheckForPrincipalChanges(true);
            filter.setAuthenticationManager(authenticationManager());
            filter.setAuthenticationFailureHandler(new IdentityHeaderAuthenticationFailureHandler(mapper));
            filter.setContinueFilterChainOnUnsuccessfulAuthentication(false);
            return filter;
        }

        @Override
        protected void configure(HttpSecurity http) throws Exception {
            // See https://docs.spring.io/spring-security/site/docs/current/reference/html5/#ns-custom-filters
            // for list of filters and their order
            http
                .requestMatchers(matchers ->
                    matchers.antMatchers("/actuator/**/jolokia", "/actuator/**/jolokia/**"))
                .csrf().disable()
                .addFilter(identityHeaderAuthenticationFilter())
                .addFilterBefore(getVerbIncludingAntiCsrfFilter(appProps, env), LogoutFilter.class)
                .authorizeRequests()
                .requestMatchers(EndpointRequest.to("jolokia")).permitAll();
        }
    }
}

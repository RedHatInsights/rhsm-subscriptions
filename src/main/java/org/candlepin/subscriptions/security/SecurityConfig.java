/*
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
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

import org.candlepin.insights.rbac.client.RbacService;
import org.candlepin.subscriptions.ApplicationProperties;
import org.candlepin.subscriptions.controller.OptInController;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

import java.util.Arrays;

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
 *   <li>IdentityHeaderAuthenticationFilter's superclass invokes the IdentityHeaderAuthenticationDetailsSource
 *       to populate the Authentication object with the
 *       PreAuthenticatedGrantedAuthoritiesWebAuthenticationDetails which contain the roles.</li>
 *   <li>The Authentication object is passed to the Spring Security AuthenticationManager.  In this case,
 *       we're using a ProviderManager with one AuthenticationProvider, IdentityHeaderAuthenticationProvider,
 *       installed.</li>
 *   <li>
 *       The IdentityHeaderAuthenticationProvider is invoked to build a blessed Authentication object.  We
 *       examine the current Authentication and make sure everything we expect is there.  Then we take the
 *       granted authorities provided from the IdentityHeaderAuthenticationDetailsSource and push them and
 *       the InsightsUserPrincipal into a new, blessed Authentication object and return it.</li>
 * </ol>
 */
@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class SecurityConfig extends WebSecurityConfigurerAdapter {
    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private ApplicationProperties appProps;

    @Autowired
    ConfigurableEnvironment env;

    @Override
    public void configure(AuthenticationManagerBuilder auth) {
        // Add our AuthenticationProvider to the Provider Manager's list
        auth.authenticationProvider(identityHeaderAuthenticationProvider());
    }

    @Bean
    public AuthenticationProvider identityHeaderAuthenticationProvider() {
        return new IdentityHeaderAuthenticationProvider();
    }

    @Bean
    public RbacService rbacService() {
        return new RbacService();
    }

    @Bean
    public IdentityHeaderAuthenticationDetailsSource detailsSource(ApplicationProperties appProps) {
        return new IdentityHeaderAuthenticationDetailsSource(
            appProps,
            identityHeaderAuthoritiesMapper(),
            rbacService()
        );
    }

    // NOTE: intentionally *not* annotated w/ @Bean; @Bean causes an *extra* use as an application filter
    public IdentityHeaderAuthenticationFilter identityHeaderAuthenticationFilter(
        ApplicationProperties appProps) throws Exception {

        IdentityHeaderAuthenticationFilter filter = new IdentityHeaderAuthenticationFilter(mapper);
        filter.setCheckForPrincipalChanges(true);
        filter.setAuthenticationManager(authenticationManager());
        filter.setAuthenticationDetailsSource(detailsSource(appProps));
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
            .addFilter(identityHeaderAuthenticationFilter(appProps))
            .csrf().disable()
            .exceptionHandling()
                .accessDeniedHandler(restAccessDeniedHandler())
                .authenticationEntryPoint(restAuthenticationEntryPoint())
            .and()
            // disable sessions, our API is stateless, and sessions cause RBAC information to be cached
            .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            .and()
            .anonymous()  // Creates an anonymous user if no header is present at all. Prevents NPEs basically
            .and()
            .authorizeRequests()
                // Allow access to Actuator endpoints here
                .requestMatchers(EndpointRequest.to("health", "info", "prometheus")).permitAll()
                .antMatchers("/**/openapi.*", "/**/version", "/api-docs/**", "/webjars/**").permitAll()
                // ingress security is done via server settings (require ssl cert auth), so permit all here
                .antMatchers(String.format("/%s/ingress/**", apiPath)).permitAll()
                .anyRequest().authenticated();
    }

}

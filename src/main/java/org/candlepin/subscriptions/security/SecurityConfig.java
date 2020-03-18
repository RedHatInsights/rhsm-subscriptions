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

import org.candlepin.subscriptions.ApplicationProperties;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationProvider;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedGrantedAuthoritiesUserDetailsService;

import java.util.Arrays;

/**
 * Configuration class for Spring Security
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
        auth.authenticationProvider(preAuthenticatedAuthenticationProvider());
    }

    @Bean
    public PreAuthenticatedAuthenticationProvider preAuthenticatedAuthenticationProvider() {
        PreAuthenticatedAuthenticationProvider provider = new PreAuthenticatedAuthenticationProvider();
        provider.setPreAuthenticatedUserDetailsService(preAuthenticatedUserDetailsService());
        return provider;
    }

    @Bean
    public PreAuthenticatedGrantedAuthoritiesUserDetailsService preAuthenticatedUserDetailsService() {
        return new PreAuthenticatedGrantedAuthoritiesUserDetailsService();
    }

    @Bean
    public AuthenticationManager identityHeaderAuthenticationManager() {
        return new IdentityHeaderAuthenticationManager(mapper);
    }

    @Bean
    public IdentityHeaderAuthenticationDetailsSource detailsSource(ApplicationProperties appProps) {
        return new IdentityHeaderAuthenticationDetailsSource(
            appProps, mapper, identityHeaderAuthoritiesMapper()
        );
    }

    @Bean
    public IdentityHeaderAuthenticationFilter identityHeaderAuthenticationFilter(
        ApplicationProperties appProps) {

        IdentityHeaderAuthenticationFilter filter = new IdentityHeaderAuthenticationFilter();
        filter.setCheckForPrincipalChanges(true);
        filter.setAuthenticationManager(identityHeaderAuthenticationManager());
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

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        String apiPath = env.getRequiredProperty(
            "rhsm-subscriptions.package_uri_mappings.org.candlepin.subscriptions");
        http
            .addFilter(identityHeaderAuthenticationFilter(appProps))
            .authenticationProvider(preAuthenticatedAuthenticationProvider())
            .exceptionHandling()
                .accessDeniedHandler(restAccessDeniedHandler())
                .authenticationEntryPoint(restAuthenticationEntryPoint())
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
        if (Arrays.asList(env.getActiveProfiles()).contains("capacity-ingress")) {
            configureForIngressEndpoint(http);
        }
    }

    @SuppressWarnings("squid:S4502")
    private void configureForIngressEndpoint(HttpSecurity http) throws Exception {
        // CSRF isn't helpful for the machine-to-machine ingress endpoint
        http.csrf().disable();
    }
}

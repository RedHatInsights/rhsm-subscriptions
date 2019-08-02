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

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationProvider;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedGrantedAuthoritiesUserDetailsService;

/**
 * Configuration class for Spring Security
 */
@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class SecurityConfig extends WebSecurityConfigurerAdapter {
    @Autowired
    private ObjectMapper mapper;

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
    public IdentityHeaderAuthenticationFilter identityHeaderAuthenticationFilter(ObjectMapper objectMapper)
        throws Exception {
        IdentityHeaderAuthenticationFilter filter = new IdentityHeaderAuthenticationFilter(objectMapper);
        filter.setCheckForPrincipalChanges(true);
        filter.setAuthenticationManager(authenticationManager());
        filter.setAuthenticationDetailsSource(detailsSource(objectMapper));
        return filter;
    }

    @Bean
    public IdentityHeaderAuthenticationDetailsSource detailsSource(ObjectMapper objectMapper) {
        return new IdentityHeaderAuthenticationDetailsSource(
            objectMapper, identityHeaderAuthoritiesMapper()
        );
    }

    @Bean
    public IdentityHeaderAuthoritiesMapper identityHeaderAuthoritiesMapper() {
        return new IdentityHeaderAuthoritiesMapper();
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
            .addFilter(identityHeaderAuthenticationFilter(mapper))
            .authenticationProvider(preAuthenticatedAuthenticationProvider())
            .anonymous()  // Creates an anonymous user if no header is present at all. Prevents NPEs basically
            .and()
            .authorizeRequests()
                .antMatchers("/**/openapi.*", "/actuator/**").permitAll()
                .anyRequest().authenticated();
    }
}

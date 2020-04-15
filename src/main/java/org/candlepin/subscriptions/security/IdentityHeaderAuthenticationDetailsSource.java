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

import org.candlepin.insights.rbac.client.RbacApi;
import org.candlepin.insights.rbac.client.RbacApiException;
import org.candlepin.insights.rbac.client.model.Access;
import org.candlepin.subscriptions.ApplicationProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationDetailsSource;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.mapping.Attributes2GrantedAuthoritiesMapper;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedGrantedAuthoritiesWebAuthenticationDetails;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

/**
 * Class in charge of populating the security context with the users roles based on the values in the
 * x-rh-identity header.
 */
public class IdentityHeaderAuthenticationDetailsSource implements
    AuthenticationDetailsSource<HttpServletRequest,
    PreAuthenticatedGrantedAuthoritiesWebAuthenticationDetails> {

    private static final Logger log =
        LoggerFactory.getLogger(IdentityHeaderAuthenticationDetailsSource.class);

    private Attributes2GrantedAuthoritiesMapper authMapper;
    private ApplicationProperties props;
    private RbacApi rbac;
    private String rbacApplicationName;
    private RoleProvider roleProvider;

    public IdentityHeaderAuthenticationDetailsSource(ApplicationProperties props,
        Attributes2GrantedAuthoritiesMapper authMapper,
        RbacApi rbac, String rbacApplicationName) {
        this.authMapper = authMapper;
        this.props = props;
        this.rbac = rbac;
        this.rbacApplicationName = rbacApplicationName;

        if (props.isDevMode()) {
            log.info("Running in DEV mode. Security will be disabled.");
        }
        this.roleProvider = new RoleProvider(rbacApplicationName, props.isDevMode());
    }

    protected Collection<String> getUserRoles() {
        List<String> permissions = new LinkedList<>();
        if (!props.isDevMode()) {
            try {
                // Get all permissions for the configured application name.
                List<Access> accessList = rbac.getCurrentUserAccess(rbacApplicationName);
                for (Access access : accessList) {
                    if (access.getPermission() != null) {
                        permissions.add(access.getPermission());
                    }
                }
            }
            catch (RbacApiException e) {
                log.warn("Unable to determine roles from RBAC service.", e);
            }
        }
        return roleProvider.getRoles(permissions);
    }

    @Override
    public PreAuthenticatedGrantedAuthoritiesWebAuthenticationDetails buildDetails(
        HttpServletRequest context) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            // We've already populated the roles if the auth has been set in the context.
            return (PreAuthenticatedGrantedAuthoritiesWebAuthenticationDetails) auth.getDetails();
        }

        Collection<String> userRoles = getUserRoles();
        Collection<? extends GrantedAuthority> userGAs = authMapper.getGrantedAuthorities(userRoles);

        log.debug("Roles {} mapped to Granted Authorities: {}", userRoles, userGAs);

        return new PreAuthenticatedGrantedAuthoritiesWebAuthenticationDetails(context, userGAs);
    }

    public void setUserRoles2GrantedAuthoritiesMapper(
        Attributes2GrantedAuthoritiesMapper authMapper) {
        this.authMapper = authMapper;
    }

}

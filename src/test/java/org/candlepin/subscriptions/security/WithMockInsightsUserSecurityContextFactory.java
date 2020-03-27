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

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithSecurityContextFactory;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class WithMockInsightsUserSecurityContextFactory implements
    WithSecurityContextFactory<WithMockRedHatPrincipal> {

    @Override
    public SecurityContext createSecurityContext(WithMockRedHatPrincipal annotation) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();

        String account = annotation.nullifyAccount() ? null : String.format("account%s", annotation.value());
        String ownerId = annotation.nullifyOwner() ? null : String.format("owner%s", annotation.value());

        InsightsUserPrincipal principal = new InsightsUserPrincipal(ownerId, account);

        List<SimpleGrantedAuthority> authorities = Arrays.stream(annotation.roles())
            .map(SimpleGrantedAuthority::new)
            .collect(Collectors.toList());

        Authentication auth =
            new UsernamePasswordAuthenticationToken(principal, null, authorities);
        context.setAuthentication(auth);

        return context;
    }
}

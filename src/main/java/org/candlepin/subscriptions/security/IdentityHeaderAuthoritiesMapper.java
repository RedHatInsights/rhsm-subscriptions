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

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.Attributes2GrantedAuthoritiesMapper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Class responsible for returning GrantedAuthorities objects back to Spring.  This is a little
 * over-engineered considering our requirements, but it fits in with the Spring Security worldview.
 */
public class IdentityHeaderAuthoritiesMapper implements Attributes2GrantedAuthoritiesMapper {
    private static final String ATTRIBUTE_PREFIX = "ROLE_";

    @Override
    public Collection<? extends GrantedAuthority> getGrantedAuthorities(Collection<String> attributes) {
        List<GrantedAuthority> result = new ArrayList<>(attributes.size());
        for (String attribute : attributes) {
            result.add(getGrantedAuthority(attribute));
        }
        return result;
    }

    private GrantedAuthority getGrantedAuthority(String attribute) {
        attribute = attribute.toUpperCase(Locale.getDefault());
        if (!attribute.startsWith(ATTRIBUTE_PREFIX)) {
            return new SimpleGrantedAuthority(ATTRIBUTE_PREFIX + attribute);
        }
        else {
            return new SimpleGrantedAuthority(attribute);
        }
    }
}

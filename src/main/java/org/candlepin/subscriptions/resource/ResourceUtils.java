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
package org.candlepin.subscriptions.resource;

import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.exception.ErrorCode;
import org.candlepin.subscriptions.exception.SubscriptionsException;
import org.candlepin.subscriptions.security.IdentityHeaderAuthenticationFilter;
import org.candlepin.subscriptions.security.InsightsUserPrincipal;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.core.Response;

/**
 * Functionality common to both capacity and tally resources.
 */
public class ResourceUtils {

    private static final Integer DEFAULT_LIMIT = 50;

    private ResourceUtils() {
        throw new IllegalStateException("Utility class; should never be instantiated!");
    }

    /**
     * Get the owner ID of the authenticated user.
     *
     * @return ownerId as a String
     */
    static String getOwnerId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        InsightsUserPrincipal principal = (InsightsUserPrincipal) auth.getPrincipal();
        return principal.getOwnerId();
    }

    /**
     * Get the account number of the authenticated user.
     *
     * @return account number as a String
     */
    static String getAccountNumber() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        InsightsUserPrincipal principal = (InsightsUserPrincipal) auth.getPrincipal();
        return principal.getAccountNumber();
    }

    /**
     * Gets the identity header passed when the request was made. Useful
     * when it has to be forwarded to other APIs.
     *
     * @return the encoded identity header.
     */
    public static String getIdentityHeader() {
        HttpServletRequest request =
            ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        return request.getHeader(IdentityHeaderAuthenticationFilter.RH_IDENTITY_HEADER);
    }

    /**
     * Validates offset and limit parameters and produces a {@link Pageable} for them.
     *
     * @param offset 0-based offset, can be null.
     * @param limit max number of items per-page, should be non-zero.
     * @return Pageable holding paging information.
     */
    @NotNull
    static Pageable getPageable(Integer offset, Integer limit) {
        if (limit == null) {
            limit = DEFAULT_LIMIT;
        }

        if (offset == null) {
            offset = 0;
        }

        if (offset % limit != 0) {
            throw new SubscriptionsException(
                ErrorCode.VALIDATION_FAILED_ERROR,
                Response.Status.BAD_REQUEST,
                "Offset must be divisible by limit",
                "Arbitrary offsets are not currently supported by this API"
            );
        }
        return PageRequest.of(offset / limit, limit);
    }

    /**
     * Validate and reject bad usage.
     *
     * throws a BadRequestException if the usage value is bad.
     *
     * @param usage string form of usage
     * @return Usage enum
     */
    public static Usage sanitizeUsage(String usage) {
        Usage sanitizedUsage = Usage.fromString(usage);
        // If the usage parameter is not one that we support, then throw an exception.
        // If we don't, the query would default to UNSPECIFIED, which would be confusing.
        if (StringUtils.hasLength(usage) && sanitizedUsage == Usage.UNSPECIFIED) {
            throw new BadRequestException("Invalid usage parameter specified.");
        }
        return sanitizedUsage;
    }

    /**
     * Validate and reject bad sla.
     *
     * throws a BadRequestException if the sla value is bad.
     *
     * @param sla string form of sla
     * @return ServiceLevel enum
     */
    public static ServiceLevel sanitizeServiceLevel(String sla) {
        ServiceLevel sanitizedSla = ServiceLevel.fromString(sla);
        // If the sla parameter is not one that we support, then throw an exception.
        // If we don't, the query would default to UNSPECIFIED, which would be confusing.
        if (StringUtils.hasLength(sla) && sanitizedSla == ServiceLevel.UNSPECIFIED) {
            throw new BadRequestException("Invalid sla parameter specified.");
        }
        return sanitizedSla;
    }
}

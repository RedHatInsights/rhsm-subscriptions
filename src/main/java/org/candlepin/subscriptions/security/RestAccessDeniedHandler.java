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

import org.candlepin.subscriptions.exception.ErrorCode;
import org.candlepin.subscriptions.exception.ExceptionUtil;
import org.candlepin.subscriptions.utilization.api.model.Error;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;
import java.io.OutputStream;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

/**
 * Entry point to allow returning a JSON response.  This handler is invoked when a client requests a
 * resource they are not authorized to access.
 */
public class RestAccessDeniedHandler implements AccessDeniedHandler {
    private static final Logger log = LoggerFactory.getLogger(RestAccessDeniedHandler.class);
    private final ObjectMapper mapper;

    public RestAccessDeniedHandler(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void handle(HttpServletRequest servletRequest, HttpServletResponse servletResponse,
        AccessDeniedException accessDeniedException) throws IOException, ServletException {
        Error error = buildError(accessDeniedException);
        log.error(error.getTitle(), accessDeniedException);

        Response r = ExceptionUtil.toResponse(error);
        servletResponse.setContentType(r.getMediaType().toString());
        servletResponse.setStatus(r.getStatus());

        OutputStream out = servletResponse.getOutputStream();
        mapper.writeValue(out, r.getEntity());
        out.flush();
    }

    protected Error buildError(AccessDeniedException exception) {
        return new Error()
            .code(ErrorCode.REQUEST_PROCESSING_ERROR.getCode())
            .status(String.valueOf(Status.FORBIDDEN.getStatusCode()))
            .title("Access Denied")
            .detail(exception.getMessage());
    }
}

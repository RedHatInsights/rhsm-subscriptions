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
import org.candlepin.subscriptions.exception.mapper.BaseExceptionMapper;
import org.candlepin.subscriptions.utilization.api.model.Error;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;
import java.io.OutputStream;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

/**
 * Entry point to allow returning a JSON response.  An error will be returned if the x-rh-identity header is
 * absent and the client will be informed that authorization is required.
 */
public class RestAuthenticationEntryPoint extends BaseExceptionMapper<AuthenticationException>
    implements AuthenticationEntryPoint {

    private final ObjectMapper mapper;

    public RestAuthenticationEntryPoint(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void commence(HttpServletRequest servletRequest, HttpServletResponse servletResponse,
        AuthenticationException authException) throws IOException, ServletException {

        Response r = toResponse(authException);
        servletResponse.setContentType(r.getMediaType().toString());
        servletResponse.setStatus(r.getStatus());

        OutputStream out = servletResponse.getOutputStream();
        mapper.writeValue(out, r.getEntity());
        out.flush();
    }

    @Override
    protected Error buildError(AuthenticationException exception) {
        return new Error()
            .code(ErrorCode.REQUEST_PROCESSING_ERROR.getCode())
            .status(String.valueOf(Status.UNAUTHORIZED.getStatusCode()))
            .title("Authorization Required")
            .detail(exception.getMessage());
    }
}

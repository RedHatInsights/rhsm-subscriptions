/*
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 * Red Hat trademarks are not licensed under GPLv2. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package org.candlepin.insights.exception.mapper;

import org.candlepin.insights.exception.NotReadyException;

import org.springframework.stereotype.Component;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

/**
 * Maps a NotReadyException to a 503 (Service Unavailable) response
 */
@Component
@Provider
public class NotReadyExceptionMapper implements ExceptionMapper<NotReadyException> {
    @Override
    public Response toResponse(NotReadyException exception) {
        return Response
            .status(Status.SERVICE_UNAVAILABLE)
            .entity(exception.getMessage())
            .type(MediaType.TEXT_PLAIN)
            .build();
    }
}

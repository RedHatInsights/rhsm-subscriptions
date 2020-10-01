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
package org.candlepin.subscriptions.exception.mapper;

import org.candlepin.subscriptions.api.model.Error;
import org.candlepin.subscriptions.api.model.Errors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;


/**
 * The base class for all rhsm-conduit exception mappers. Its intent is to build the same
 * response no matter the exception.
 * @param <T> the throwable that is to be mapped
 */
public abstract class BaseExceptionMapper<T extends Throwable> implements ExceptionMapper<T> {

    private static final Logger log = LoggerFactory.getLogger(BaseExceptionMapper.class);

    // Media type required per the jsonapi.org API spec. JAXRS doesn't provide
    // this type as a constant, so we define it ourselves.
    // TODO We may need to move this media type somewhere common.
    protected static final String MEDIA_TYPE = "application/vnd.api+json";

    @Override
    public Response toResponse(T exception) {
        Error error = buildError(exception);
        logError(error, exception);

        // IMPL NOTE:
        //   The jsonapi.org spec requires that an Error response should be a
        //   collection of Error objects in a dictionary with an 'errors' key
        //   in case the server should want to return multiple errors in a single
        //   response. While we likely won't ever need to do this, we'll conform
        //   to the spec anyhow.
        Errors errors = new Errors().errors(new ArrayList<>(Collections.singleton(error)));
        return Response.status(Integer.valueOf(error.getStatus()))
            .entity(errors)
            .type(MEDIA_TYPE)
            .build();
    }

    /**
     * Builds an Error model object that will be returned as the body of an
     * error response.
     *
     * @param exception the exception to be transformed.
     * @return an Error object containing the appropriate code and message
     *         as deduced from the passed exception.
     */
    abstract Error buildError(T exception);

    private void logError(Error error, Throwable exception) {
        String message = error.getCode() != null && !error.getCode().isEmpty() ?
            String.format("%s: %s", error.getCode(), error.getTitle()) : error.getTitle();
        log.error(message, exception);
    }

}

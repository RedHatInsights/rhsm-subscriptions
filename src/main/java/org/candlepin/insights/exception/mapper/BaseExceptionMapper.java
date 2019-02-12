/*
 * Copyright (c) 2016 - 2019 Red Hat, Inc.
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

import org.candlepin.insights.api.model.Error;
import org.candlepin.insights.api.model.Errors;

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

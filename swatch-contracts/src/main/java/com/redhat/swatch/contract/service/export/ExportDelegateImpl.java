/*
 * Copyright Red Hat, Inc.
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
package com.redhat.swatch.contract.service.export;

import static com.redhat.swatch.clowder.DependencyEndpointSelection.VERSION_V1;
import static com.redhat.swatch.clowder.DependencyEndpointSelection.VERSION_V2;
import static com.redhat.swatch.export.ExportRequestHandler.INTERNAL_ERROR;

import com.redhat.swatch.clients.export.api.model.DownloadExportErrorRequest;
import com.redhat.swatch.clients.export.api.resources.ApiException;
import com.redhat.swatch.clients.export.api.resources.ExportApi;
import com.redhat.swatch.clowder.DependencyEndpointResolver;
import com.redhat.swatch.clowder.DependencyEndpointSelection;
import com.redhat.swatch.clowder.DependencyEndpointUnavailable;
import com.redhat.swatch.export.ExportServiceException;
import com.redhat.swatch.export.ExportServiceRequest;
import com.redhat.swatch.export.api.ExportDelegate;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import jakarta.ws.rs.core.Response;
import java.io.File;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
public class ExportDelegateImpl implements ExportDelegate {

  private final DependencyEndpointResolver endpointResolver;
  private final ExportServiceApiFactory apiFactory;

  public ExportDelegateImpl(
      @Named("export-service") DependencyEndpointResolver endpointResolver,
      ExportServiceApiFactory apiFactory) {
    this.endpointResolver = endpointResolver;
    this.apiFactory = apiFactory;
  }

  @Override
  public void sendExportError(ExportServiceRequest request, Integer error, String message)
      throws ExportServiceException {
    invoke(
        request.getExportRequestUUID(),
        "Error marking the export as error",
        api ->
            api.downloadExportError(
                request.getExportRequestUUID(),
                request.getApplication(),
                request.getRequest().getUUID(),
                new DownloadExportErrorRequest().error(error).message(message)));
  }

  @Override
  public void upload(File file, ExportServiceRequest request) throws ExportServiceException {
    invoke(
        request.getExportRequestUUID(),
        "Error sending upload request",
        api ->
            api.downloadExportUpload(
                request.getExportRequestUUID(),
                request.getApplication(),
                request.getRequest().getUUID(),
                file));
  }

  private void invoke(Object requestId, String failureMessage, ExportApiCall call)
      throws ExportServiceException {
    DependencyEndpointSelection selection = endpointResolver.current();
    try {
      call.execute(apiFactory.clientFor(selection));
    } catch (ApiException | RuntimeException firstFailure) {
      if (!shouldFallbackToV1(firstFailure)) {
        throw toExportServiceException(requestId, failureMessage, firstFailure);
      }
      boolean authFailure = isAuthFailure(firstFailure);
      boolean switchedToV1 = endpointResolver.tryFallbackToV1();
      DependencyEndpointSelection fallback = endpointResolver.current();
      // Retry on V1 if this call switched, or another call already did.
      if (!switchedToV1
          && !(VERSION_V2.equals(selection.version()) && VERSION_V1.equals(fallback.version()))) {
        throw toExportServiceException(requestId, failureMessage, firstFailure);
      }
      if (authFailure) {
        // ERROR is the alert signal for V2 auth path structural failure (not a silent fallback).
        log.error(
            "export-service V2 auth failure for request {}, falling back to V1 ({})",
            requestId,
            fallback.uri(),
            firstFailure);
      } else {
        log.warn(
            "export-service {} unavailable for request {}, falling back to {} ({})",
            selection.version(),
            requestId,
            fallback.version(),
            fallback.uri(),
            firstFailure);
      }
      try {
        call.execute(apiFactory.clientFor(fallback));
      } catch (ApiException | RuntimeException secondFailure) {
        throw toExportServiceException(requestId, failureMessage, secondFailure);
      }
    }
  }

  /**
   * Connectivity failures or HTTP 401/403 from V2 allow a one-time V1 retry. Token-service failures
   * must not: that would downgrade authenticated V2 (Bearer) to V1 (PSK).
   */
  static boolean shouldFallbackToV1(Throwable error) {
    if (isTokenAcquisitionFailure(error)) {
      return false;
    }
    return DependencyEndpointUnavailable.isUnavailable(error) || isAuthFailure(error);
  }

  private static boolean isTokenAcquisitionFailure(Throwable error) {
    Throwable current = error;
    while (current != null) {
      if (current instanceof ExportTokenAcquisitionException) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }

  private static boolean isAuthFailure(Throwable error) {
    Throwable current = error;
    while (current != null) {
      if (current instanceof ApiException apiException) {
        Response response = apiException.getResponse();
        if (response != null) {
          int status = response.getStatus();
          if (status == Response.Status.UNAUTHORIZED.getStatusCode()
              || status == Response.Status.FORBIDDEN.getStatusCode()) {
            return true;
          }
        }
      }
      current = current.getCause();
    }
    return false;
  }

  private static ExportServiceException toExportServiceException(
      Object requestId, String failureMessage, Throwable error) {
    log.error("{} for request {}", failureMessage, requestId, error);
    Exception cause = error instanceof Exception exception ? exception : new Exception(error);
    return new ExportServiceException(
        INTERNAL_ERROR, failureMessage + " with message: " + error.getMessage(), cause);
  }

  @FunctionalInterface
  private interface ExportApiCall {
    void execute(ExportApi api) throws ApiException;
  }
}

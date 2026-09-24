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
package com.redhat.swatch.kessel;

import io.grpc.CallCredentials;
import io.grpc.Metadata;
import io.grpc.SecurityLevel;
import io.grpc.Status;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/** Fetch credentials on the gRPC executor, and never send a bearer token over plaintext. */
public class HccCallCredentials extends CallCredentials {
  private static final Metadata.Key<String> AUTHORIZATION =
      Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);
  private final Supplier<String> authorization;

  public HccCallCredentials(Supplier<String> authorization) {
    this.authorization = authorization;
  }

  @Override
  public void applyRequestMetadata(
      RequestInfo requestInfo, Executor appExecutor, MetadataApplier applier) {
    if (requestInfo.getSecurityLevel() != SecurityLevel.PRIVACY_AND_INTEGRITY) {
      applier.fail(
          Status.UNAUTHENTICATED.withDescription("HCC workload authentication requires TLS"));
      return;
    }
    appExecutor.execute(
        () -> {
          try {
            var metadata = new Metadata();
            metadata.put(AUTHORIZATION, authorization.get());
            applier.apply(metadata);
          } catch (RuntimeException e) {
            // Do not propagate token endpoint responses, client secrets, or tokens into gRPC logs.
            applier.fail(
                Status.UNAUTHENTICATED.withDescription("HCC workload authentication unavailable"));
          }
        });
  }
}

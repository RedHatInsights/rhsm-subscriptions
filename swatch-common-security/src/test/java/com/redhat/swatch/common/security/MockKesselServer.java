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
package com.redhat.swatch.common.security;

import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.project_kessel.api.inventory.v1beta2.Allowed;
import org.project_kessel.api.inventory.v1beta2.CheckRequest;
import org.project_kessel.api.inventory.v1beta2.CheckResponse;
import org.project_kessel.api.inventory.v1beta2.KesselInventoryServiceGrpc;
import org.project_kessel.api.inventory.v1beta2.KesselInventoryServiceGrpc.KesselInventoryServiceBlockingStub;

/**
 * In-process gRPC mock for the Kessel Inventory Service. Use in component and integration tests to
 * verify the full Kessel authorization flow without a real Kessel deployment.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * MockKesselServer kessel = new MockKesselServer();
 * kessel.start();
 *
 * // Allow all checks by default
 * kessel.allowAll();
 *
 * // Or configure specific permissions
 * kessel.setResponse("subscriptions_all_all", Allowed.ALLOWED_TRUE);
 * kessel.setResponse("subscriptions_reports_read", Allowed.ALLOWED_FALSE);
 *
 * // Get a blocking stub for direct use or injection
 * KesselInventoryServiceBlockingStub stub = kessel.blockingStub();
 *
 * // Cleanup
 * kessel.stop();
 * }</pre>
 */
public class MockKesselServer {

  private final String serverName;
  private Server server;
  private ManagedChannel channel;
  private final Map<String, Allowed> responses = new ConcurrentHashMap<>();
  private Allowed defaultResponse = Allowed.ALLOWED_FALSE;

  public MockKesselServer() {
    this.serverName = InProcessServerBuilder.generateName();
  }

  public void start() throws IOException {
    server =
        InProcessServerBuilder.forName(serverName)
            .directExecutor()
            .addService(new MockKesselInventoryService())
            .build()
            .start();
    channel = InProcessChannelBuilder.forName(serverName).directExecutor().build();
  }

  public void stop() {
    if (channel != null) {
      channel.shutdownNow();
    }
    if (server != null) {
      server.shutdownNow();
    }
  }

  public KesselInventoryServiceBlockingStub blockingStub() {
    return KesselInventoryServiceGrpc.newBlockingStub(channel);
  }

  public void setResponse(String relation, Allowed allowed) {
    responses.put(relation, allowed);
  }

  public void allowAll() {
    defaultResponse = Allowed.ALLOWED_TRUE;
    responses.clear();
  }

  public void denyAll() {
    defaultResponse = Allowed.ALLOWED_FALSE;
    responses.clear();
  }

  public void reset() {
    responses.clear();
    defaultResponse = Allowed.ALLOWED_FALSE;
  }

  private class MockKesselInventoryService
      extends KesselInventoryServiceGrpc.KesselInventoryServiceImplBase {

    @Override
    public void check(CheckRequest request, StreamObserver<CheckResponse> responseObserver) {
      var relation = request.getRelation();
      var allowed = responses.getOrDefault(relation, defaultResponse);
      responseObserver.onNext(CheckResponse.newBuilder().setAllowed(allowed).build());
      responseObserver.onCompleted();
    }
  }
}

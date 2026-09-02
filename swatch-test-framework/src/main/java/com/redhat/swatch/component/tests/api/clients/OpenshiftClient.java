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
package com.redhat.swatch.component.tests.api.clients;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.okhttp.OkHttpClientFactory;
import io.fabric8.openshift.client.OpenShiftClient;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.Dispatcher;

public final class OpenshiftClient extends BaseKubernetesClient<OpenShiftClient> {

  @Override
  public OpenShiftClient initializeClient(Config config) {
    return new KubernetesClientBuilder()
        .withConfig(config)
        .withHttpClientFactory(new BoundedOkHttpClientFactory())
        .build()
        .adapt(OpenShiftClient.class);
  }

  static final class BoundedOkHttpClientFactory extends OkHttpClientFactory {
    static final int MAX_REQUESTS = 32;
    static final int MAX_REQUESTS_PER_HOST = 16;
    static final int MAX_DISPATCHER_THREADS = 32;

    @Override
    protected Dispatcher initDispatcher() {
      AtomicInteger threadIndex = new AtomicInteger();
      ThreadPoolExecutor executor =
          new ThreadPoolExecutor(
              0,
              MAX_DISPATCHER_THREADS,
              60L,
              TimeUnit.SECONDS,
              new SynchronousQueue<>(),
              runnable -> newDispatcherThread(runnable, threadIndex),
              new ThreadPoolExecutor.CallerRunsPolicy());
      Dispatcher dispatcher = new Dispatcher(executor);
      dispatcher.setMaxRequests(MAX_REQUESTS);
      dispatcher.setMaxRequestsPerHost(MAX_REQUESTS_PER_HOST);
      return dispatcher;
    }

    private static Thread newDispatcherThread(Runnable runnable, AtomicInteger threadIndex) {
      Thread thread = new Thread(runnable, "okhttp-ct-dispatcher-" + threadIndex.incrementAndGet());
      thread.setDaemon(true);
      return thread;
    }
  }
}

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
package com.redhat.swatch.aws.test.resources;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import java.util.HashMap;
import java.util.Map;

public class InMemoryMessageBrokerKafkaResource implements QuarkusTestResourceLifecycleManager {

  @Override
  public Map<String, String> start() {
    Map<String, String> env = new HashMap<>();
    Map<String, String> props1 = InMemoryConnector.switchIncomingChannelsToInMemory("tally-in");
    Map<String, String> props2 = InMemoryConnector.switchOutgoingChannelsToInMemory("tally-out");
    env.putAll(props1);
    env.putAll(props2);
    return env;
  }

  @Override
  public void stop() {
    InMemoryConnector.clear();
  }
}

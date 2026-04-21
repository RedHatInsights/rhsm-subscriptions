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
package com.redhat.swatch.utilization.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.swatch.utilization.configuration.FeatureFlags;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationsProducerTest {

  private static final String ORG_ID = "org123";

  @Mock FeatureFlags featureFlags;

  @Mock Emitter<Action> emitter;

  NotificationsProducer producer;

  @BeforeEach
  void setUp() {
    lenient().when(emitter.hasRequests()).thenReturn(true);
    producer = new NotificationsProducer(featureFlags, emitter);
  }

  @Test
  void shouldNotEmit_whenActionIsNull() {
    producer.produce(null);

    verify(emitter, never()).send(any(Message.class));
  }

  @Test
  void shouldEmit_whenSendNotificationsFlagEnabled() {
    when(featureFlags.sendNotifications()).thenReturn(true);
    Action action = new Action();
    action.setOrgId(ORG_ID);

    producer.produce(action);

    verify(emitter).send(any(Message.class));
  }

  @Test
  void shouldEmit_whenOrgIsAllowlistedAndGlobalSendNotificationsDisabled() {
    when(featureFlags.sendNotifications()).thenReturn(false);
    when(featureFlags.isOrgAllowlistedForNotifications(ORG_ID)).thenReturn(true);
    Action action = new Action();
    action.setOrgId(ORG_ID);

    producer.produce(action);

    verify(emitter).send(any(Message.class));
  }

  @Test
  void shouldNotEmit_whenOrgIsNotAllowlistedAndGlobalSendNotificationsDisabled() {
    when(featureFlags.sendNotifications()).thenReturn(false);
    when(featureFlags.isOrgAllowlistedForNotifications(ORG_ID)).thenReturn(false);
    Action action = new Action();
    action.setOrgId(ORG_ID);

    producer.produce(action);

    verify(emitter, never()).send(any(Message.class));
  }
}

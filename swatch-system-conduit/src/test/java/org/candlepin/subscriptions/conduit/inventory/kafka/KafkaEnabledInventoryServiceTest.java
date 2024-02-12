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
package org.candlepin.subscriptions.conduit.inventory.kafka;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.candlepin.subscriptions.conduit.inventory.ConduitFacts;
import org.candlepin.subscriptions.conduit.inventory.InventoryServiceProperties;
import org.candlepin.subscriptions.conduit.json.inventory.HbiFactSet;
import org.candlepin.subscriptions.utilization.api.model.ConsumerInventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.retry.backoff.NoBackOffPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ExtendWith(MockitoExtension.class)
@ActiveProfiles({"rhsm-conduit", "test", "kafka-queue"})
class KafkaEnabledInventoryServiceTest {

  private static final int NUMBER_OF_CPUS = 5;
  private static final int THREADS_PER_CORE = 3;

  @Autowired
  @Qualifier("kafkaRetryTemplate")
  private RetryTemplate retryTemplate;

  @Mock private KafkaTemplate producer;

  @Mock MeterRegistry meterRegistry;

  @Mock Counter mockCounter;

  @BeforeEach
  void setup() {
    when(meterRegistry.counter(any())).thenReturn(mockCounter);

    // Make the tests run faster!
    retryTemplate.setBackOffPolicy(new NoBackOffPolicy());
  }

  @Test
  void ensureKafkaProducerSendsHostMessage() {
    ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<CreateUpdateHostMessage> messageCaptor =
        ArgumentCaptor.forClass(CreateUpdateHostMessage.class);

    when(producer.send(topicCaptor.capture(), messageCaptor.capture())).thenReturn(null);

    ConduitFacts expectedFacts = new ConduitFacts();
    expectedFacts.setFqdn("my_fqdn");
    expectedFacts.setOrgId("my_org");
    expectedFacts.setCpuCores(25);
    expectedFacts.setCpuSockets(45);
    expectedFacts.setOsName("Red Hat Enterprise Linux Workstation");
    expectedFacts.setOsVersion("6.3");
    expectedFacts.setNumberOfCpus(NUMBER_OF_CPUS);
    expectedFacts.setThreadsPerCore(THREADS_PER_CORE);
    expectedFacts.setProviderType(ConsumerInventory.ProviderTypeEnum.AZURE);
    expectedFacts.setProviderId(UUID.randomUUID().toString());

    InventoryServiceProperties props = new InventoryServiceProperties();
    props.setKafkaHostIngressTopic("placeholder");
    KafkaEnabledInventoryService service =
        new KafkaEnabledInventoryService(props, producer, meterRegistry, retryTemplate);
    service.sendHostUpdate(Arrays.asList(expectedFacts));

    assertEquals(props.getKafkaHostIngressTopic(), topicCaptor.getValue());

    CreateUpdateHostMessage message = messageCaptor.getValue();
    assertNotNull(message);
    assertEquals("add_host", message.getOperation());
    assertEquals(expectedFacts.getFqdn(), message.getData().getFqdn());

    assertNotNull(message.getData().getFacts());
    assertEquals(1, message.getData().getFacts().size());
    HbiFactSet rhsm = message.getData().getFacts().get(0);
    assertEquals("rhsm", rhsm.getNamespace());

    Map<String, Object> rhsmFacts = (Map<String, Object>) rhsm.getFacts();
    assertEquals(expectedFacts.getOrgId(), (String) rhsmFacts.get("orgId"));

    OffsetDateTime syncDate = (OffsetDateTime) rhsmFacts.get("SYNC_TIMESTAMP");
    assertNotNull(syncDate);
    assertEquals(syncDate, message.getData().getStaleTimestamp());
    assertEquals("rhsm-conduit", message.getData().getReporter());
    assertEquals("6.3", message.getData().getSystemProfile().getOsRelease());
    assertEquals(
        "RHEL", message.getData().getSystemProfile().getOperatingSystem().getName().value());
    assertEquals(6, message.getData().getSystemProfile().getOperatingSystem().getMajor());
    assertEquals(3, message.getData().getSystemProfile().getOperatingSystem().getMinor());
    assertEquals(NUMBER_OF_CPUS, message.getData().getSystemProfile().getNumberOfCpus());
    assertEquals(THREADS_PER_CORE, message.getData().getSystemProfile().getThreadsPerCore());
    assertEquals(expectedFacts.getProviderId(), message.getData().getProviderId());
    assertEquals(expectedFacts.getProviderType(), message.getData().getProviderType());
  }

  @Test
  void ensureSendHostRetries() {
    ConduitFacts conduitFacts = new ConduitFacts();
    List<ConduitFacts> expectedFacts = Arrays.asList(conduitFacts);

    when(producer.send(anyString(), any(CreateUpdateHostMessage.class)))
        .thenThrow(KafkaException.class);

    InventoryServiceProperties props = new InventoryServiceProperties();
    props.setKafkaHostIngressTopic("placeholder");
    KafkaEnabledInventoryService service =
        new KafkaEnabledInventoryService(props, producer, meterRegistry, retryTemplate);
    service.sendHostUpdate(expectedFacts);

    // This 4 is based on the RetryTemplate.  I don't know of a way to get the value at runtime, so
    // it's
    // hardcoded here.
    verify(producer, times(4)).send(anyString(), any(CreateUpdateHostMessage.class));
  }

  @Test
  void ensureNoMessageWithEmptyFactList() {
    InventoryServiceProperties props = new InventoryServiceProperties();
    KafkaEnabledInventoryService service =
        new KafkaEnabledInventoryService(props, producer, meterRegistry, retryTemplate);
    service.sendHostUpdate(Arrays.asList());

    verifyNoInteractions(producer);
  }

  @Test
  void ensureMessageSentWhenHostUpdateScheduled() {
    InventoryServiceProperties props = new InventoryServiceProperties();
    props.setKafkaHostIngressTopic("placeholder");
    KafkaEnabledInventoryService service =
        new KafkaEnabledInventoryService(props, producer, meterRegistry, retryTemplate);
    service.scheduleHostUpdate(new ConduitFacts());
    service.scheduleHostUpdate(new ConduitFacts());

    verify(producer, times(2)).send(anyString(), any());
  }

  @Test
  void testStaleTimestampUpdatedBasedOnSyncTimestampAndOffset() {
    ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<CreateUpdateHostMessage> messageCaptor =
        ArgumentCaptor.forClass(CreateUpdateHostMessage.class);

    when(producer.send(topicCaptor.capture(), messageCaptor.capture())).thenReturn(null);

    ConduitFacts expectedFacts = new ConduitFacts();

    InventoryServiceProperties props = new InventoryServiceProperties();
    props.setStaleHostOffset(Duration.ofHours(24));

    KafkaEnabledInventoryService service =
        new KafkaEnabledInventoryService(props, producer, meterRegistry, retryTemplate);
    service.sendHostUpdate(Arrays.asList(expectedFacts));

    CreateUpdateHostMessage message = messageCaptor.getValue();
    assertNotNull(message);
    assertEquals("add_host", message.getOperation());

    assertNotNull(message.getData().getFacts());
    assertEquals(1, message.getData().getFacts().size());
    HbiFactSet rhsm = message.getData().getFacts().get(0);
    assertEquals("rhsm", rhsm.getNamespace());

    Map<String, Object> rhsmFacts = (Map<String, Object>) rhsm.getFacts();
    OffsetDateTime syncDate = (OffsetDateTime) rhsmFacts.get("SYNC_TIMESTAMP");
    assertNotNull(syncDate);
    assertEquals(syncDate.plusHours(24), message.getData().getStaleTimestamp());
  }

  @ParameterizedTest
  @MethodSource("osReleases")
  void requiredKafkaProducerSendsHostMessageHasMajorVersion(
      String release, Integer expectedMajorVersion, Integer expectedMinorVersion) {
    ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<CreateUpdateHostMessage> messageCaptor =
        ArgumentCaptor.forClass(CreateUpdateHostMessage.class);

    when(producer.send(topicCaptor.capture(), messageCaptor.capture())).thenReturn(null);

    ConduitFacts expectedFacts = new ConduitFacts();
    expectedFacts.setFqdn("my_fqdn");
    expectedFacts.setOrgId("my_org");
    expectedFacts.setCpuCores(25);
    expectedFacts.setCpuSockets(45);
    expectedFacts.setOsName("Red Hat Enterprise Linux Workstation");
    expectedFacts.setOsVersion(release);

    InventoryServiceProperties props = new InventoryServiceProperties();
    props.setKafkaHostIngressTopic("placeholder");
    KafkaEnabledInventoryService service =
        new KafkaEnabledInventoryService(props, producer, meterRegistry, retryTemplate);
    service.sendHostUpdate(Arrays.asList(expectedFacts));

    assertEquals(props.getKafkaHostIngressTopic(), topicCaptor.getValue());

    CreateUpdateHostMessage message = messageCaptor.getValue();
    assertNotNull(message);
    assertEquals("add_host", message.getOperation());
    assertEquals(expectedFacts.getFqdn(), message.getData().getFqdn());

    assertNotNull(message.getData().getFacts());
    assertEquals(1, message.getData().getFacts().size());
    HbiFactSet rhsm = message.getData().getFacts().get(0);
    assertEquals("rhsm", rhsm.getNamespace());

    Map<String, Object> rhsmFacts = (Map<String, Object>) rhsm.getFacts();
    assertEquals(expectedFacts.getOrgId(), (String) rhsmFacts.get("orgId"));

    OffsetDateTime syncDate = (OffsetDateTime) rhsmFacts.get("SYNC_TIMESTAMP");
    assertNotNull(syncDate);
    assertEquals(syncDate, message.getData().getStaleTimestamp());
    assertEquals("rhsm-conduit", message.getData().getReporter());
    assertEquals(release, message.getData().getSystemProfile().getOsRelease());
    assertEquals(
        "RHEL", message.getData().getSystemProfile().getOperatingSystem().getName().value());
    assertEquals(
        expectedMajorVersion,
        message.getData().getSystemProfile().getOperatingSystem().getMajor(),
        "Unexpected major version");
    assertEquals(
        expectedMinorVersion,
        message.getData().getSystemProfile().getOperatingSystem().getMinor(),
        "Unexpected minor version");
  }

  @ParameterizedTest
  @MethodSource("osNullableVersions")
  void testHandleEmptyOrNonFormatOsVersion(String version) {
    ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<CreateUpdateHostMessage> messageCaptor =
        ArgumentCaptor.forClass(CreateUpdateHostMessage.class);

    when(producer.send(topicCaptor.capture(), messageCaptor.capture())).thenReturn(null);

    ConduitFacts expectedFacts = new ConduitFacts();
    expectedFacts.setFqdn("my_fqdn");
    expectedFacts.setOrgId("my_org");
    expectedFacts.setCpuCores(25);
    expectedFacts.setCpuSockets(45);
    expectedFacts.setOsName("Red Hat Enterprise Linux Workstation");
    expectedFacts.setOsVersion(version);

    InventoryServiceProperties props = new InventoryServiceProperties();
    props.setKafkaHostIngressTopic("placeholder");
    KafkaEnabledInventoryService service =
        new KafkaEnabledInventoryService(props, producer, meterRegistry, retryTemplate);
    service.sendHostUpdate(Arrays.asList(expectedFacts));

    assertEquals(props.getKafkaHostIngressTopic(), topicCaptor.getValue());

    CreateUpdateHostMessage message = messageCaptor.getValue();
    assertNotNull(message);
    assertEquals("add_host", message.getOperation());
    assertEquals(expectedFacts.getFqdn(), message.getData().getFqdn());

    assertNotNull(message.getData().getFacts());
    assertEquals(1, message.getData().getFacts().size());
    HbiFactSet rhsm = message.getData().getFacts().get(0);
    assertEquals("rhsm", rhsm.getNamespace());

    Map<String, Object> rhsmFacts = (Map<String, Object>) rhsm.getFacts();
    assertEquals(expectedFacts.getOrgId(), (String) rhsmFacts.get("orgId"));

    OffsetDateTime syncDate = (OffsetDateTime) rhsmFacts.get("SYNC_TIMESTAMP");
    assertNotNull(syncDate);
    assertEquals(syncDate, message.getData().getStaleTimestamp());
    assertEquals("rhsm-conduit", message.getData().getReporter());
    assertEquals(version, message.getData().getSystemProfile().getOsRelease());
    assertNull(message.getData().getSystemProfile().getOperatingSystem());
  }

  static Stream<Arguments> osReleases() {
    return Stream.of(
        Arguments.of("11.54.3.4", 11, 54),
        Arguments.of("11.54.3", 11, 54),
        Arguments.of("5.14", 5, 14),
        Arguments.of("6", 6, 0));
  }

  static Stream<Arguments> osNullableVersions() {
    return Stream.of(
        Arguments.of(""), Arguments.of("5-0"), Arguments.of("unknown"), Arguments.of("7server"));
  }
}

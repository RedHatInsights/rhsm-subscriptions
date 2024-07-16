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
package org.candlepin.subscriptions.tally.admin;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.fasterxml.jackson.databind.util.StdDateFormat;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.jakarta.xmlbind.JakartaXmlBindAnnotationModule;
import jakarta.ws.rs.BadRequestException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import org.candlepin.clock.ApplicationClock;
import org.candlepin.subscriptions.ApplicationProperties;
import org.candlepin.subscriptions.db.EventRecordRepository;
import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.retention.TallyRetentionController;
import org.candlepin.subscriptions.security.SecurityProperties;
import org.candlepin.subscriptions.tally.MarketplaceResendTallyController;
import org.candlepin.subscriptions.tally.events.EventRecordsRetentionProperties;
import org.candlepin.subscriptions.tally.job.CaptureSnapshotsTaskManager;
import org.candlepin.subscriptions.task.TaskQueueProperties;
import org.candlepin.subscriptions.test.TestClockConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

@ExtendWith(MockitoExtension.class)
class InternalTallyResourceTest {

  private static final String ORG_ID = "org1";

  @Mock private MarketplaceResendTallyController resendTallyController;
  @Mock private CaptureSnapshotsTaskManager snapshotTaskManager;
  @Mock private TallyRetentionController tallyRetentionController;
  @Mock private InternalTallyDataController internalTallyDataController;
  @Mock private SecurityProperties properties;
  @Mock private EventRecordRepository eventRecordRepository;
  @Mock private KafkaTemplate<String, Event> kafkaTemplate;

  private EventRecordsRetentionProperties eventRecordsRetentionProperties;
  private InternalTallyResource resource;
  private ApplicationProperties appProps;
  private ApplicationClock clock;
  private TaskQueueProperties taskQueueProperties;

  @BeforeEach
  void setupTest() {
    clock = new TestClockConfiguration().adjustableClock();
    appProps = new ApplicationProperties();
    eventRecordsRetentionProperties = new EventRecordsRetentionProperties();
    taskQueueProperties = new TaskQueueProperties();
    taskQueueProperties.setTopic("platform.rhsm-subscriptions.service-instance-ingress");
    resource =
        new InternalTallyResource(
            clock,
            appProps,
            resendTallyController,
            snapshotTaskManager,
            tallyRetentionController,
            internalTallyDataController,
            properties,
            eventRecordRepository,
            eventRecordsRetentionProperties,
            objectMapper(appProps),
            kafkaTemplate,
            taskQueueProperties);
  }

  @Test
  void testSaveEvents() {
    when(properties.isDevMode()).thenReturn(true);
    resource.saveEvents(
        "[{"
            + "  \"sla\": \"Premium\","
            + "  \"role\": \"moa-hostedcontrolplane\","
            + "  \"org_id\": \"11091977\","
            + "  \"event_id\": \"e2afa8c0-63de-4f55-8ded-66d088c439c4\","
            + "  \"timestamp\": \"2024-07-10T03:00:00Z\","
            + "  \"conversion\": false,"
            + "  \"event_type\": \"snapshot_rosa_cores\","
            + "  \"expiration\": \"2024-07-11T04:00:00Z\","
            + "  \"instance_id\": \"73e5401b-52ff-4fb2-adcd-ed151ed9b1bd\","
            + "  \"product_ids\": [],"
            + "  \"product_tag\": ["
            + "    \"rosa\""
            + "  ],"
            + "  \"record_date\": \"2024-07-02T04:31:06.608885043Z\", "
            + "  \"display_name\": \"psl-rosa-nprod\","
            + "  \"event_source\": \"prometheus\","
            + "  \"measurements\": ["
            + "    {"
            + "      \"value\": 13.333333333333334,"
            + "      \"metric_id\": \"Cores\""
            + "    }"
            + "  ],"
            + "  \"service_type\": \"rosa Instance\","
            + "  \"billing_provider\": \"aws\","
            + "  \"metering_batch_id\": \"f39d1d22-eb72-4597-b722-5bee34abd78d\","
            + "  \"billing_account_id\": \"381492115198\""
            + " }]");
    verify(kafkaTemplate, times(1)).send(eq(taskQueueProperties.getTopic()), any(Event.class));
  }

  @Test
  void allowSynchronousHourlyTallyForOrgWhenSynchronousOperationsEnabled() {
    appProps.setEnableSynchronousOperations(true);
    resource.performHourlyTallyForOrg(ORG_ID, true);
    verify(snapshotTaskManager).tallyOrgByHourly("org1", true);
  }

  @Test
  void performAsyncHourlyTallyForOrgWhenSynchronousOperationsEnabled() {
    appProps.setEnableSynchronousOperations(true);
    resource.performHourlyTallyForOrg("org1", false);
    verify(snapshotTaskManager).tallyOrgByHourly("org1", false);
  }

  @Test
  void performAsyncHourlyTallyForOrgWhenSynchronousOperationsDisabled() {
    resource.performHourlyTallyForOrg(ORG_ID, false);
    verify(snapshotTaskManager).tallyOrgByHourly(ORG_ID, false);
  }

  @Test
  void testTallyOrgWhenAsyncRequest() {
    resource.tallyOrg(ORG_ID, false);
    verify(internalTallyDataController).tallyOrg(ORG_ID);
  }

  @Test
  void testTallyOrgWhenAsyncRequestAsNull() {
    resource.tallyOrg(ORG_ID, null);
    verify(internalTallyDataController).tallyOrg(ORG_ID);
  }

  @Test
  void testTallyOrgWhenSyncRequestAndNotConfigured() {
    appProps.setEnableSynchronousOperations(false);
    assertThrows(BadRequestException.class, () -> resource.tallyOrg(ORG_ID, true));
  }

  @Test
  void testTallyOrgWhenSyncRequestAndConfigured() {
    appProps.setEnableSynchronousOperations(true);
    resource.tallyOrg(ORG_ID, true);
    verify(internalTallyDataController).tallyOrgSync(ORG_ID);
  }

  @Test
  void testDeleteDataAssociatedWithOrg() {
    when(properties.isDevMode()).thenReturn(true);
    resource.deleteDataAssociatedWithOrg(ORG_ID);
    verify(internalTallyDataController).deleteDataAssociatedWithOrg(ORG_ID);
  }

  @Test
  void testPurgeEventRecords() {
    OffsetDateTime expectedRetentionTarget =
        clock.now().truncatedTo(ChronoUnit.DAYS).minusMonths(6);
    resource.purgeEventRecords();
    verify(eventRecordRepository)
        .deleteInBulkEventRecordsByTimestampBefore(expectedRetentionTarget);
  }

  ObjectMapper objectMapper(ApplicationProperties applicationProperties) {
    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    objectMapper.setDateFormat(new StdDateFormat().withColonInTimeZone(true));
    objectMapper.configure(
        SerializationFeature.INDENT_OUTPUT, applicationProperties.isPrettyPrintJson());
    objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    objectMapper.setAnnotationIntrospector(new JacksonAnnotationIntrospector());

    // Explicitly load the modules we need rather than use ObjectMapper.findAndRegisterModules in
    // order to avoid com.fasterxml.jackson.module.scala.DefaultScalaModule, which was causing
    // deserialization to ignore @JsonProperty on OpenApi classes.
    objectMapper.registerModule(new JakartaXmlBindAnnotationModule());
    objectMapper.registerModule(new JavaTimeModule());
    objectMapper.registerModule(new Jdk8Module());

    return objectMapper;
  }
}

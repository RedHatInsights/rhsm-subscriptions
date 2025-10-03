package utils;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.redhat.swatch.component.tests.utils.Topics.SERVICE_INSTANCE_INGRESS;
import static com.redhat.swatch.component.tests.utils.Topics.TALLY;

import com.redhat.swatch.component.tests.api.KafkaBridge;
import com.redhat.swatch.events.payloads.EventPayload;

public class TallyTestHelpers {
    private static final String SERVICE_INSTANCE_INGRESS = "platform.rhsm-subscriptions.service-instance-ingress";

    @KafkaBridge
    private KafkaBridge kafkaBridge;

    private TallyTestHelpers(){}
    
    public EventPayload createEventPayload(
            String eventSource, String eventType, String orgId, String instanceId, String displayName,
            float value, String metricId
    ){
        OffsetDateTime prevHourStart = OffsetDateTime.now().minusHours(1).truncatedTo(ChronoUnit.HOURS);
        OffsetDateTime prevHourEnd = prevHourStart.plusHours(1).minusNanos(1);


        // Create EventPayload
        var payload = new EventPayload();

        // Set basic fields
        payload.setEventSource(eventSource);
        payload.setEventType(eventType);
        payload.setOrgId(orgId);
        payload.setInstanceId(instanceId);
        payload.setDisplayName(displayName);

        // Set timestamps
        payload.setTimestamp(prevHourStart.withOffsetSameInstant(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        payload.setExpiration(prevHourEnd.withOffsetSameInstant(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));

        // Create and set measurement
        var measurement = Map.of(
                "value", value,
                "metric_id", metricId
        );
        payload.setMeasurements(List.of(measurement));

        // Set additional fields
        payload.setSla("Premium");
        payload.setServiceType("rosa Instance");
        payload.setRole("moa-hostedcontrolplane");
        payload.setBillingProvider("aws");
        payload.setBillingAccountId(UUID.randomUUID());

        return payload;
    }

    public void syncTallyByOrgId(String orgId) throws Exception{
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(" /v1/internal/rpc/tally/snapshots/{org_id}")) // Replace with your API endpoint
                .header("Authorization", "Bearer YOUR_API_KEY")
                .GET() // or POST, PUT, DELETE
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        System.out.println("Status Code: " + response.statusCode());
        System.out.println("Response Body: " + response.body());
    }
}

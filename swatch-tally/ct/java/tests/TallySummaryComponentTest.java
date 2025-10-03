package tests;

public class TallySummaryComponentTest extends BaseTallyComponentTest{
    @Test
    public void testTallySummaryHourlyGranularity(){
        // Test setup

        // Create Event
        EventPayload event = createEventPayload();

        // Submit Event
        kafkaBridge.produceKafkaMessage(SERVICE_INSTANCE_INGRESS, eventPayload);

        //Sync Tally

        //Read Tally Topic


    }

    @Test
    public void testTallySummaryDailyGranularity(){

    }
}
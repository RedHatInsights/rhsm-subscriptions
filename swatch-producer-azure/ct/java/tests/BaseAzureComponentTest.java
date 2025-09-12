package tests;

import static com.redhat.swatch.component.tests.utils.Topics.BILLABLE_USAGE_STATUS;

import api.AzureWiremockService;
import com.redhat.swatch.component.tests.api.ComponentTest;
import com.redhat.swatch.component.tests.api.KafkaBridge;
import com.redhat.swatch.component.tests.api.KafkaBridgeService;
import com.redhat.swatch.component.tests.api.Quarkus;
import com.redhat.swatch.component.tests.api.SwatchService;
import com.redhat.swatch.component.tests.api.Wiremock;
import org.junit.jupiter.api.Tag;

@ComponentTest
@Tag("component")
@Tag("azure")
public class BaseAzureComponentTest {

  @KafkaBridge
  static KafkaBridgeService kafkaBridge =
      new KafkaBridgeService().subscribeToTopic(BILLABLE_USAGE_STATUS);

  @Wiremock
  static AzureWiremockService wiremock = new AzureWiremockService();

  @Quarkus(service = "swatch-producer-azure")
  static SwatchService service = new SwatchService();

}

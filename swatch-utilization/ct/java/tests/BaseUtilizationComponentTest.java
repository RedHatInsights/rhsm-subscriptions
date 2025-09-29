package tests;

import static com.redhat.swatch.component.tests.utils.Topics.BILLABLE_USAGE_STATUS;
import static com.redhat.swatch.component.tests.utils.Topics.UTILIZATION;

import com.redhat.swatch.component.tests.api.ComponentTest;
import com.redhat.swatch.component.tests.api.KafkaBridge;
import com.redhat.swatch.component.tests.api.KafkaBridgeService;
import com.redhat.swatch.component.tests.api.Quarkus;
import com.redhat.swatch.component.tests.api.SwatchService;
import org.junit.jupiter.api.Tag;

@ComponentTest
@Tag("component")
@Tag("utilization")
public class BaseUtilizationComponentTest {

  @KafkaBridge
  static KafkaBridgeService kafkaBridge = new KafkaBridgeService();

  @Quarkus(service = "swatch-utilization")
  static SwatchService service = new SwatchService();

}

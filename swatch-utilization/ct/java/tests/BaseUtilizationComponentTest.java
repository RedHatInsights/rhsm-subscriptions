package tests;

import com.redhat.swatch.component.tests.api.ComponentTest;
import com.redhat.swatch.component.tests.api.Quarkus;
import com.redhat.swatch.component.tests.api.SwatchService;
import org.junit.jupiter.api.Tag;

@ComponentTest
@Tag("component")
@Tag("utilization")
public class BaseUtilizationComponentTest {

  @Quarkus(service = "swatch-utilization")
  static SwatchService service = new SwatchService();

}

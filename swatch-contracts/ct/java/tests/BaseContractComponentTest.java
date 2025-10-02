package tests;

import api.ContractsWiremockService;
import com.redhat.swatch.component.tests.api.ComponentTest;
import com.redhat.swatch.component.tests.api.Quarkus;
import com.redhat.swatch.component.tests.api.SwatchService;
import com.redhat.swatch.component.tests.api.Wiremock;
import org.junit.jupiter.api.Tag;

@ComponentTest
@Tag("component")
@Tag("contracts")
public class BaseContractComponentTest {

  @Wiremock
  static ContractsWiremockService wiremock = new ContractsWiremockService();

  @Quarkus(service = "swatch-contracts")
  static SwatchService service = new SwatchService();

}
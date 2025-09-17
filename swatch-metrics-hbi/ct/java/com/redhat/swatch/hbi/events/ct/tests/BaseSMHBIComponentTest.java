package com.redhat.swatch.hbi.events.ct.tests;

import com.redhat.swatch.component.tests.api.ComponentTest;
import com.redhat.swatch.component.tests.api.KafkaBridge;
import com.redhat.swatch.component.tests.api.KafkaBridgeService;
import com.redhat.swatch.component.tests.api.Quarkus;
import com.redhat.swatch.component.tests.api.SwatchService;
import com.redhat.swatch.component.tests.utils.Topics;
import org.junit.jupiter.api.Tag;

@ComponentTest
@Tag("component")
@Tag("azure")
public class BaseSMHBIComponentTest {
  @KafkaBridge
  static KafkaBridgeService kafkaBridge =
      new KafkaBridgeService()
          .subscribeToTopic(Topics.HBI_EVENT_IN);

  @Quarkus(service = "swatch-metrics-hbi")
  static SwatchService service = new SwatchService();

}

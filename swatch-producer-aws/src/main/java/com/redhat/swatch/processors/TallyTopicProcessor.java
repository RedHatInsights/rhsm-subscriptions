package com.redhat.swatch.processors;

import com.redhat.swatch.openapi.model.TallySummary;
import javax.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@ApplicationScoped
public class TallyTopicProcessor {

  private static final Logger log = LoggerFactory.getLogger(TallyTopicProcessor.class);

  private static final String INGRESS_CHANNEL = "ingress";

  @Incoming(INGRESS_CHANNEL)
  public void process(TallySummary tallySummary) {

    log.info("Picked up tally message {} to process", tallySummary);

  }
}

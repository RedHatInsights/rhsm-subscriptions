package com.redhat.swatch.processors;

import io.smallrye.common.annotation.Blocking;
import javax.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@ApplicationScoped
public class TallyTopicProcessor {

  private static final String INGRESS_CHANNEL = "ingress";

  @Incoming(INGRESS_CHANNEL)
  @Blocking
  public void process(String tallyMessage) {
    System.err.println("In TallyTopicProcessor class.  message: " + tallyMessage);
  }


}

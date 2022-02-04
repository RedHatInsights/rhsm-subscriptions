package org.acme.processors;

import io.smallrye.common.annotation.Blocking;
import javax.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@ApplicationScoped
public class FruitSaladRequestProcessor {

  // "requests" maps to part of the app property key mp.messaging.incoming.requests.topic
  @Incoming("requests")
  @Blocking
  public void process(String fruitSaladRequest) throws InterruptedException {
    System.err.println("In fruit salad request processor class.  message: " + fruitSaladRequest);
  }


}

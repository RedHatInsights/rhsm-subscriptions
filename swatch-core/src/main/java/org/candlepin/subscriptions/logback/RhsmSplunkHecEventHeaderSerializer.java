package org.candlepin.subscriptions.logback;

import com.splunk.logging.EventHeaderSerializer;
import com.splunk.logging.HttpEventCollectorEventInfo;

import java.util.HashMap;
import java.util.Map;

public class RhsmSplunkHecEventHeaderSerializer implements EventHeaderSerializer {
  @Override
  public Map<String, Object> serializeEventHeader(
      HttpEventCollectorEventInfo eventInfo, Map<String, Object> metadata) {

    var fields = (Map<String, Object>) metadata.getOrDefault("fields", new HashMap<>());

    // TODO this better
    fields.put("namespace", System.getenv("SPLUNKMETA_namespace"));

    return metadata;
  }
}

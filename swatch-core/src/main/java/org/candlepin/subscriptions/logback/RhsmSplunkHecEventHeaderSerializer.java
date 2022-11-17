/*
 * Copyright Red Hat, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Red Hat trademarks are not licensed under GPLv3. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package org.candlepin.subscriptions.logback;

import com.splunk.logging.EventHeaderSerializer;
import com.splunk.logging.HttpEventCollectorEventInfo;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Customized EventHeaderSerializer for setting additional splunk event information that's not
 * currently supported by default via logback xml config by splunk javalogging library. Refer to
 * https://github.com/splunk/splunk-library-javalogging/blob/873187d16d0fd66e54adb0f0edf45ad5d195c94c/src/test/resources/logback_template.xml
 * for event information that can be configured via the logback xml file.
 */
@SuppressWarnings("java:S1135")
public class RhsmSplunkHecEventHeaderSerializer implements EventHeaderSerializer {
  @Override
  public Map<String, Object> serializeEventHeader(
      HttpEventCollectorEventInfo eventInfo, Map<String, Object> metadata) {
    metadata.put("time", String.format(Locale.US, "%.3f", eventInfo.getTime()));
    var fields = (Map<String, Object>) metadata.getOrDefault("fields", new HashMap<>());

    // TODO
    /*
     * We should find a more elegant implementation for this, but the loading of this class happens
     * at a point in the startup lifecycle where putting it into a @Bean configurable via
     * application properties wasn't a straightforward solution.
     */
    fields.put("namespace", System.getenv("SPLUNKMETA_namespace"));

    return metadata;
  }
}

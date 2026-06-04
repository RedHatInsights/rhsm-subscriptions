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

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.boolex.EvaluationException;
import ch.qos.logback.core.boolex.EventEvaluatorBase;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A Logback EventEvaluator that filters messages based on the contents of the MDC. This class
 * exists to cope with the problem that where exceptions are thrown is often very different from
 * where they are logged. Many times we do not want to print a stacktrace for a more mundane
 * exception, but at logging time we no longer have the context to make that decision (especially if
 * the exception is a very general one like BadRequestException).
 *
 * <p>This class is similar in design to the {@link ch.qos.logback.classic.boolex.OnMarkerEvaluator}
 * class. With this evaluator, classes can add an item to the thread-wide MDC when an exception is
 * thrown and then when it is time to log the exception in the {@link
 * org.candlepin.subscriptions.exception.mapper.BaseExceptionMapper}, we can use the MDC contents to
 * decide whether to print a full stacktrace or not.
 *
 * <p>This evaluator looks for given MDC keys that are set to "true".
 */
public class OnMdcEvaluator extends EventEvaluatorBase<ILoggingEvent> {
  List<String> mdcKeyList = new ArrayList<>();

  public void addMdcKey(String mdcKey) {
    mdcKeyList.add(mdcKey);
  }

  @Override
  public boolean evaluate(ILoggingEvent event) throws NullPointerException, EvaluationException {
    Map<String, String> mdcPropertyMap = event.getMDCPropertyMap();

    if (mdcPropertyMap == null) {
      return false;
    }

    return mdcPropertyMap.entrySet().stream()
        .anyMatch(
            x -> Boolean.TRUE.toString().equals(x.getValue()) && mdcKeyList.contains(x.getKey()));
  }
}
